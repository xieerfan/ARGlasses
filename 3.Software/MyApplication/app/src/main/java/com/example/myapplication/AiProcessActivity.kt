package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✅ 改进的AiProcessActivity
 *
 * 改进点：
 * 1. 去除3个Tab栏
 * 2. 单页面实时刷新
 * 3. ✅ 新增：显示JSON结果文件
 */
class AiProcessActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AiProcessActivity"
        private const val CLEANUP_TAG = "CLEANUP"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isTransferring = false
    private var isBleProcessing = false

    private lateinit var imageEnhancer: ImageEnhancer
    private lateinit var imageSplitter: ImageSplitter
    private lateinit var cameraHelper: CameraHelper
    private lateinit var processingManager: ImageProcessingManager

    private val _isBleConnected = MutableStateFlow(false)
    private val _isProcessing = MutableStateFlow(false)
    val isBleConnected: StateFlow<Boolean> = _isBleConnected
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) cameraHelper.takePicture() }

    private val receivedImages = mutableListOf<File>()
    private val enhancedImages = mutableListOf<File>()
    private val splitImages = mutableListOf<Pair<String, List<File>>>()
    private val receiveProgress = mutableListOf<String>()
    private val jsonResults = mutableListOf<File>()  // ✅ 新增：JSON结果文件列表

    private val _receivedImagesCount = MutableStateFlow(0)
    private val _enhancedImagesCount = MutableStateFlow(0)
    private val _progressLogsCount = MutableStateFlow(0)
    private val _jsonResultsCount = MutableStateFlow(0)  // ✅ 新增：JSON结果计数
    val receivedImagesCount: StateFlow<Int> = _receivedImagesCount
    val enhancedImagesCount: StateFlow<Int> = _enhancedImagesCount
    val progressLogsCount: StateFlow<Int> = _progressLogsCount
    val jsonResultsCount: StateFlow<Int> = _jsonResultsCount

    private var processingJob: Job? = null

    private val imagesDir by lazy { File(filesDir, "images") }
    private val enhancedDir by lazy { File(imagesDir, "enhanced") }
    private val regionsDir by lazy { File(imagesDir, "regions") }
    private val resultsDir by lazy { File(filesDir, "results") }

    private val requestImageRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isTransferring) {
                MainActivity.bleManager.sendCommand("takeimage")
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "✨ Activity创建")

        imageEnhancer = ImageEnhancer(this)
        imageSplitter = ImageSplitter(this)
        processingManager = ImageProcessingManager(this)

        if (!processingManager.initialize()) {
            Log.w(TAG, "⚠️  处理管理器初始化失败")
        }

        cameraHelper = CameraHelper(
            activity = this,
            onPhotoCaptured = { addPhotoToReceivedImages(it) },
            onError = { addProgressLog("❌ 拍照失败: $it") }
        )

        // ✅ 初始化时加载JSON结果
        loadJsonResults()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AiProcessScreenV6(
                        activity = this@AiProcessActivity,
                        processingManager = processingManager,
                        onBack = { finish() },
                        imageEnhancer = imageEnhancer,
                        imageSplitter = imageSplitter,
                        receivedImages = receivedImages,
                        enhancedImages = enhancedImages,
                        splitImages = splitImages,
                        receiveProgress = receiveProgress,
                        jsonResults = jsonResults,
                        onTakePicture = { cameraHelper.takePictureWithPermission(permissionLauncher) },
                        onStartProcess = { subject -> startProcessing(subject) },
                        onDeleteAll = { deleteAllImages() },
                        isBleConnected = isBleConnected,
                        isProcessing = isProcessing,
                        receivedImagesCount = receivedImagesCount,
                        enhancedImagesCount = enhancedImagesCount,
                        progressLogsCount = progressLogsCount,
                        jsonResultsCount = jsonResultsCount
                    )
                }
            }
        }

        startAutoRequest()
        setupBleCallbacks()
    }

    // ✅ 新增：加载JSON结果文件
    private fun loadJsonResults() {
        try {
            jsonResults.clear()
            val resultDirs = listOf(
                File(filesDir, "results"),  // 旧位置
                resultsDir  // 新位置
            )

            resultDirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles { file ->
                        file.extension == "json"
                    }?.forEach { jsonFile ->
                        if (!jsonResults.contains(jsonFile)) {
                            jsonResults.add(jsonFile)
                            Log.d(TAG, "📄 加载JSON结果: ${jsonFile.name}")
                        }
                    }
                }
            }

            _jsonResultsCount.value = jsonResults.size
            Log.d(TAG, "✅ JSON结果加载完成，共${jsonResults.size}个")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加载JSON结果失败: ${e.message}", e)
        }
    }

    private fun setupBleCallbacks() {
        lifecycleScope.launch {
            MainActivity.bleManager.logs.collect { logs ->
                logs.lastOrNull()?.let { msg ->
                    when {
                        msg.contains("image_ready") -> {
                            isTransferring = true
                            addProgressLog("📥 开始接收图片...")
                        }
                        msg.contains("image_end") -> {
                            isTransferring = false
                            addProgressLog("✅ 图片接收完成")
                        }
                        msg.contains("连接") -> {
                            _isBleConnected.value = true
                            addProgressLog("🔗 BLE已连接")
                        }
                        msg.contains("断开") -> {
                            _isBleConnected.value = false
                            addProgressLog("⚠️ BLE已断开")
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            MainActivity.bleManager.receivedImage.collect { imageData ->
                imageData?.let { saveReceivedImage(it) }
            }
        }

        lifecycleScope.launch {
            MainActivity.bleManager.receivedCommand.collect { command ->
                if (command == "ai_work") {
                    Log.d(TAG, "📱 收到BLE处理命令: ai_work")

                    if (isBleProcessing) {
                        Log.w(TAG, "⚠️  已在处理中，忽略重复的BLE命令")
                        return@collect
                    }

                    isBleProcessing = true
                    addProgressLog("🤖 收到BLE处理命令")

                    val subject = "physics"
                    startProcessing(subject)

                    isBleProcessing = false
                }
            }
        }
    }

    private fun saveReceivedImage(imageData: ByteArray) {
        try {
            val dir = File(filesDir, "original_images").apply { mkdirs() }
            val file = File(dir, "image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { it.write(imageData) }
            receivedImages.add(file)
            _receivedImagesCount.value = receivedImages.size
            addProgressLog("📷 接收图片: ${file.name} (共${receivedImages.size}张)")
        } catch (e: Exception) {
            addProgressLog("❌ 保存失败: ${e.message}")
        }
    }

    private fun addPhotoToReceivedImages(photoFile: File) {
        try {
            val dir = File(filesDir, "original_images").apply { mkdirs() }
            val copiedFile = File(dir, "photo_${System.currentTimeMillis()}.jpg")
            photoFile.copyTo(copiedFile, overwrite = true)
            receivedImages.add(copiedFile)
            _receivedImagesCount.value = receivedImages.size
            addProgressLog("📸 拍照上传: ${copiedFile.name} (共${receivedImages.size}张)")
        } catch (e: Exception) {
            addProgressLog("❌ 添加失败: ${e.message}")
        }
    }

    private fun addProgressLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        receiveProgress.add("[$timestamp] $message")
        _progressLogsCount.value = receiveProgress.size
        if (receiveProgress.size > 100) receiveProgress.removeAt(0)
    }

    private fun startProcessing(subject: String) {
        if (isBleConnected.value) {
            addProgressLog("⚠️ BLE已连接，请通过设备端发起处理")
            return
        }

        if (receivedImages.isEmpty()) {
            addProgressLog("⚠️ 没有图片")
            return
        }

        processingJob?.cancel()

        processingJob = lifecycleScope.launch {
            _isProcessing.value = true
            try {
                Log.d(TAG, "🎬 开始处理，科目: $subject")
                addProgressLog("🎬 开始处理，科目: ${processingManager.getSubjectChinese(subject)}")

                processingManager.setAnalysisCallback { title, message ->
                    addProgressLog("$title: $message")
                }

                val enhancedDir = File(filesDir, "enhanced_images").apply { mkdirs() }
                val splitDir = File(filesDir, "split_images").apply { mkdirs() }

                addProgressLog("📸 第一步：图片增强...")
                val enhanced = imageEnhancer.enhanceImages(receivedImages, enhancedDir)
                enhancedImages.addAll(enhanced)
                _enhancedImagesCount.value = enhancedImages.size
                addProgressLog("✅ 增强完成: ${enhanced.size}/${receivedImages.size}")

                if (enhanced.isEmpty()) {
                    _isProcessing.value = false
                    return@launch
                }

                if (subject.lowercase() in listOf("english", "chinese", "order")) {
                    addProgressLog("⏭️  科目 $subject：不进行图片分割")
                } else {
                    addProgressLog("🔄 第二步：图片分割...")
                    for (imageFile in enhanced) {
                        val resultDir = File(splitDir, imageFile.nameWithoutExtension)
                        resultDir.mkdirs()
                        val splitResult = imageSplitter.splitImage(imageFile, resultDir)
                        if (splitResult.isNotEmpty()) {
                            splitImages.add(Pair(imageFile.name, splitResult))
                        }
                    }
                    addProgressLog("✅ 分割完成: ${splitImages.size}张")
                }

                if (subject.lowercase() != "order") {
                    addProgressLog("🤖 第三步：AI分析...")
                    val result = processingManager.processAllImages(
                        subject = subject,
                        enhancedDir = enhancedDir,
                        splitDir = splitDir,
                        scope = this
                    )

                    if (result.success) {
                        addProgressLog("✅ AI分析完成: ${result.totalAnalyzed} 张")

                        // ✅ 处理完成后重新加载JSON结果
                        loadJsonResults()
                        addProgressLog("📄 已刷新JSON结果")
                    } else {
                        addProgressLog("❌ AI分析失败: ${result.message}")
                    }
                } else {
                    addProgressLog("⏭️  科目为order，跳过AI分析")
                }

                addProgressLog("✅ 处理流程完成")
                _isProcessing.value = false

            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理异常: ${e.message}", e)
                addProgressLog("❌ 处理异常: ${e.message}")
                _isProcessing.value = false
            }
        }
    }

    private fun deleteAllImages() {
        try {
            listOf("original_images", "enhanced_images", "split_images").forEach {
                File(filesDir, it).deleteRecursively()
            }
            receivedImages.clear()
            enhancedImages.clear()
            splitImages.clear()
            _receivedImagesCount.value = 0
            _enhancedImagesCount.value = 0
            addProgressLog("🗑️  已删除所有图片")
        } catch (e: Exception) {
            addProgressLog("❌ 删除失败: ${e.message}")
        }
    }

    private fun startAutoRequest() {
        isRunning = true
        handler.post(requestImageRunnable)
    }

    override fun onDestroy() {
        Log.d(TAG, "🗑️  Activity销毁 - 开始清理")

        isRunning = false
        handler.removeCallbacks(requestImageRunnable)
        processingJob?.cancel()

        super.onDestroy()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                delay(100)
                cleanupAllFiles()
            } catch (e: Exception) {
                Log.e(CLEANUP_TAG, "❌ 清理线程异常: ${e.message}")
            }
        }

        Log.d(TAG, "🗑️  onDestroy完成")
    }

    private fun cleanupAllFiles() {
        try {
            Log.d(CLEANUP_TAG, "🧹 开始清理所有文件...")

            Log.d(CLEANUP_TAG, "📂 清理旧位置文件...")
            listOf("original_images", "enhanced_images", "split_images", "results").forEach {
                try {
                    File(filesDir, it).deleteRecursively()
                    Log.d(CLEANUP_TAG, "✅ 删除旧目录: $it")
                } catch (e: Exception) {
                    Log.w(CLEANUP_TAG, "⚠️  删除旧目录失败 $it: ${e.message}")
                }
            }

            Log.d(CLEANUP_TAG, "📂 清理新位置文件...")
            cleanupDirectory(resultsDir, "分析结果")
            cleanupDirectory(regionsDir, "分割区域图片")
            cleanupDirectory(enhancedDir, "增强后的图片")
            cleanupDirectory(imagesDir, "原始图片", deleteDir = false)

            Log.d(CLEANUP_TAG, "✅ 清理完成！所有临时文件已删除")

        } catch (e: Exception) {
            Log.e(CLEANUP_TAG, "❌ 清理失败: ${e.message}", e)
        }
    }

    private fun cleanupDirectory(directory: File, description: String, deleteDir: Boolean = true) {
        if (!directory.exists()) {
            Log.d(CLEANUP_TAG, "⏭️  目录不存在，跳过: $description")
            return
        }

        val files = directory.listFiles()
        if (files == null) {
            Log.w(CLEANUP_TAG, "⚠️  无法读取目录: $description")
            return
        }

        var deletedCount = 0
        var failedCount = 0

        files.forEach { file ->
            try {
                if (file.isDirectory) {
                    deleteDirectoryRecursively(file)
                    Log.d(CLEANUP_TAG, "📁 删除目录: ${file.name}")
                    deletedCount++
                } else {
                    if (file.delete()) {
                        Log.d(CLEANUP_TAG, "🗑️  删除文件: ${file.name}")
                        deletedCount++
                    } else {
                        Log.w(CLEANUP_TAG, "⚠️  删除失败: ${file.name}")
                        failedCount++
                    }
                }
            } catch (e: Exception) {
                Log.e(CLEANUP_TAG, "❌ 清理失败: ${file.name} - ${e.message}")
                failedCount++
            }
        }

        if (deleteDir) {
            try {
                if (directory.delete()) {
                    Log.d(CLEANUP_TAG, "📁 删除目录: ${directory.name}")
                    deletedCount++
                }
            } catch (e: Exception) {
                Log.e(CLEANUP_TAG, "❌ 删除目录失败: ${directory.name}")
            }
        }

        if (deletedCount > 0 || failedCount > 0) {
            Log.d(CLEANUP_TAG, "📊 $description - 成功删除: $deletedCount, 失败: $failedCount")
        }
    }

    private fun deleteDirectoryRecursively(directory: File): Boolean {
        return if (directory.isDirectory) {
            val children = directory.listFiles() ?: return directory.delete()
            var allDeleted = true

            for (child in children) {
                if (!deleteDirectoryRecursively(child)) {
                    allDeleted = false
                }
            }

            if (allDeleted) directory.delete() else false
        } else {
            directory.delete()
        }
    }
}

// ============ ✅ 简化UI - 无Tab栏，单页面实时刷新 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProcessScreenV6(
    activity: Context,
    processingManager: ImageProcessingManager,
    onBack: () -> Unit,
    imageEnhancer: ImageEnhancer,
    imageSplitter: ImageSplitter,
    receivedImages: MutableList<File>,
    enhancedImages: MutableList<File>,
    splitImages: MutableList<Pair<String, List<File>>>,
    receiveProgress: MutableList<String>,
    jsonResults: MutableList<File>,
    onTakePicture: () -> Unit,
    onStartProcess: (String) -> Unit,
    onDeleteAll: () -> Unit,
    isBleConnected: StateFlow<Boolean>,
    isProcessing: StateFlow<Boolean>,
    receivedImagesCount: StateFlow<Int>,
    enhancedImagesCount: StateFlow<Int>,
    progressLogsCount: StateFlow<Int>,
    jsonResultsCount: StateFlow<Int>
) {
    val bleConnected by isBleConnected.collectAsState()
    val processing by isProcessing.collectAsState()
    var selectedSubject by remember { mutableStateOf("physics") }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var jsonPreviewContent by remember { mutableStateOf<String?>(null) }  // ✅ 新增：JSON预览内容

    val receivedCount by receivedImagesCount.collectAsState()
    val enhancedCount by enhancedImagesCount.collectAsState()
    val logsCount by progressLogsCount.collectAsState()
    val jsonCount by jsonResultsCount.collectAsState()  // ✅ 新增：JSON计数

    if (previewFile != null) {
        ImagePreviewDialog(previewFile!!) { previewFile = null }
    }

    // ✅ 新增：JSON预览对话框
    if (jsonPreviewContent != null) {
        JsonPreviewDialog(jsonPreviewContent!!) { jsonPreviewContent = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 图片处理", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("收$receivedCount | 增$enhancedCount | 结$jsonCount | 日$logsCount", fontSize = 10.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    Box(
                        modifier = Modifier
                            .background(if (bleConnected) Color(0xFF2E7D32) else Color(0xFF616161), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (bleConnected) "已连接" else "未连接", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SubjectSelectionDropdown(
                    processingManager = processingManager,
                    selectedSubject = selectedSubject,
                    onSubjectSelected = { selectedSubject = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = onTakePicture,
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(8.dp, CircleShape),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "拍照", modifier = Modifier.size(48.dp), tint = Color.White)
                }
                Text("拍照", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))

                Spacer(modifier = Modifier.height(30.dp))

                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已收集照片", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$receivedCount 张", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStartProcess(selectedSubject) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !processing && receivedCount > 0 && !bleConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bleConnected) Color(0xFF9E9E9E) else Color(0xFF4CAF50)
                    )
                ) {
                    if (processing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (processing) "处理中..." else "开始处理 - ${processingManager.getSubjectChinese(selectedSubject)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDeleteAll,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除所有图片", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                if (bleConnected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BLE已连接，请通过设备端发起处理", fontSize = 12.sp, color = Color(0xFF1976D2))
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // ✅ 新增：JSON结果显示
            if (jsonResults.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI分析结果 (${jsonResults.size}个)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        items(jsonResults) { jsonFile ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            jsonPreviewContent = jsonFile.readText()
                                        } catch (e: Exception) {
                                            jsonPreviewContent = "读取失败: ${e.message}"
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
                                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(jsonFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text("${jsonFile.length() / 1024}KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("实时进度 (${receiveProgress.size}条)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (receiveProgress.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无日志", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        items(receiveProgress.reversed()) { log ->
                            LogItemV5(log)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (enhancedImages.isNotEmpty()) {
                    Text("✨ 增强后的图片 (${enhancedImages.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(enhancedImages) { file ->
                            ImageCardV5(file, onClick = { previewFile = file })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (splitImages.isNotEmpty()) {
                    Text("📑 分割后的图片 (${splitImages.size}组)", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(splitImages) { (name, files) ->
                            SplitCardV5(name, files, onClick = { previewFile = it })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (enhancedImages.isEmpty() && splitImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无结果", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ✅ 新增：JSON预览对话框
@Composable
fun JsonPreviewDialog(content: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JSON结果", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Black)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    content,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}

@Composable
fun SubjectSelectionDropdown(
    processingManager: ImageProcessingManager,
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val subjects = processingManager.getSupportedSubjects()

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "选择科目: ${processingManager.getSubjectChinese(selectedSubject)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            subjects.forEach { subject ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (subject == selectedSubject) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(processingManager.getSubjectChinese(subject))
                        }
                    },
                    onClick = {
                        onSubjectSelected(subject)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LogItemV5(log: String) {
    val (icon, color) = when {
        log.contains("❌") -> Icons.Default.Clear to Color(0xFFD32F2F)
        log.contains("✅") -> Icons.Default.CheckCircle to Color(0xFF388E3C)
        log.contains("⚠️") -> Icons.Default.Warning to Color(0xFFF57C00)
        log.contains("🤖") || log.contains("📚") -> Icons.Default.Android to Color(0xFF1976D2)
        else -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
            Text(log, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
        }
    }
}

@Composable
fun ImageCardV5(file: File, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(100.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(80.dp).background(Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("大小: ${file.length() / 1024}KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SplitCardV5(name: String, files: List<File>, onClick: (File) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                Text("${files.size} 张", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                files.take(3).forEach { file ->
                    ImageThumbnailV5(file, modifier = Modifier.weight(1f)) { onClick(file) }
                }
                if (files.size > 3) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text("+${files.size - 3}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageThumbnailV5(file: File, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .background(Color.Gray)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun ImagePreviewDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
            }

            Box(modifier = Modifier.fillMaxSize(0.9f).background(Color.DarkGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.95f),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}