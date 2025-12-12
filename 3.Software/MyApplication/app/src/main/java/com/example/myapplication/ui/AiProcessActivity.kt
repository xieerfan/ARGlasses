// 位置: com/example/myapplication/ui/AiProcessActivity.kt
package com.example.myapplication.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.AppTheme
import com.example.myapplication.CameraHelper
import com.example.myapplication.config.ConfigManager
import com.example.myapplication.ImageEnhancer
import com.example.myapplication.ImageProcessingManager
import com.example.myapplication.ImageSplitter
import com.example.myapplication.MainActivity
import com.example.myapplication.network.AnswerUploadManager
import com.example.myapplication.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ✅ 修复后的AiProcessActivity
 *
 * 改进点：
 * 1. ✅ 在onCreate时清空前次的接收图片
 * 2. ✅ 集成AnswerUploadManager
 * 3. ✅ 处理完成后自动上传答案
 * 4. 显示JSON结果文件
 * 5. 答案删除功能（本地+数据库）
 *
 * 修复：
 * - 移除receivedFiles引用，改为使用receivedImage.collect
 * - 修复类型推断问题
 * - 简化BLE回调处理
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
    private lateinit var answerUploadManager: AnswerUploadManager

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
    private val jsonResults = mutableListOf<File>()

    private val _receivedImagesCount = MutableStateFlow(0)
    private val _enhancedImagesCount = MutableStateFlow(0)
    private val _progressLogsCount = MutableStateFlow(0)
    private val _jsonResultsCount = MutableStateFlow(0)
    val receivedImagesCount: StateFlow<Int> = _receivedImagesCount
    val enhancedImagesCount: StateFlow<Int> = _enhancedImagesCount
    val progressLogsCount: StateFlow<Int> = _progressLogsCount
    val jsonResultsCount: StateFlow<Int> = _jsonResultsCount

    private var processingJob: Job? = null

    private val imagesDir by lazy { File(filesDir, "images") }
    private val enhancedDir by lazy { File(imagesDir, "enhanced") }
    private val regionsDir by lazy { File(imagesDir, "regions") }
    private val resultsDir by lazy { File(filesDir, "results") }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

        // ✅ 新增：在onCreate时清空前次的接收图片和缓存
        clearPreviousData()

        imageEnhancer = ImageEnhancer(this)
        imageSplitter = ImageSplitter(this)
        processingManager = ImageProcessingManager(this)

        // ✅ 新增：创建AnswerUploadManager
        answerUploadManager = AnswerUploadManager(MainActivity.bleManager, NetworkManager)

        // ✅ 新增：关联AnswerUploadManager到ImageProcessingManager
        processingManager.setAnswerUploadManager(answerUploadManager)

        if (!processingManager.initialize()) {
            Log.w(TAG, "⚠️  处理管理器初始化失败")
        }

        cameraHelper = CameraHelper(
            activity = this,
            onPhotoCaptured = { addPhotoToReceivedImages(it) },
            onError = { addProgressLog("❌ 拍照失败: $it") }
        )

        loadJsonResults()

        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AiProcessScreenV6(
                        activity = this@AiProcessActivity,
                        processingManager = processingManager,
                        answerUploadManager = answerUploadManager,
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
                        onDeleteAnswer = { answerId, fileName -> deleteAnswer(answerId, fileName) },
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

    /**
     * ✅ 新增：清空前次的数据
     */
    private fun clearPreviousData() {
        try {
            Log.d(TAG, "🧹 清空前次的数据...")

            // 清空列表
            receivedImages.clear()
            enhancedImages.clear()
            splitImages.clear()
            receiveProgress.clear()

            // 重置计数
            _receivedImagesCount.value = 0
            _enhancedImagesCount.value = 0
            _progressLogsCount.value = 0

            // 清空接收目录
            try {
                val originalImagesDir = File(filesDir, "original_images")
                if (originalImagesDir.exists()) {
                    originalImagesDir.deleteRecursively()
                    Log.d(TAG, "✅ 已清空原始图片目录")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  清空原始图片目录失败: ${e.message}")
            }

            // 清空images/received目录
            try {
                val imagesReceivedDir = File(imagesDir, "received")
                if (imagesReceivedDir.exists()) {
                    imagesReceivedDir.deleteRecursively()
                    Log.d(TAG, "✅ 已清空images/received目录")
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️  清空images/received目录失败: ${e.message}")
            }

            Log.d(TAG, "✅ 前次数据清空完成")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 清空前次数据失败: ${e.message}", e)
        }
    }

    private fun loadJsonResults() {
        try {
            jsonResults.clear()
            val resultDirs = listOf(
                File(filesDir, "results"),
                resultsDir
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
        // 日志收集
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

        // 接收图片数据
        lifecycleScope.launch {
            MainActivity.bleManager.receivedImage.collect { imageData ->
                imageData?.let { saveReceivedImage(it) }
            }
        }

        // 接收BLE命令
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
        Log.d(TAG, message)
    }

    private fun startProcessing(subject: String) {
        if (_isProcessing.value) {
            addProgressLog("⚠️  已有处理在进行中")
            return
        }

        processingJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                _isProcessing.value = true
                addProgressLog("🎬 开始处理流程，科目: $subject")

                // 设置进度回调
                processingManager.setAnalysisCallback { title, message ->
                    addProgressLog("$title: $message")
                }

                // 执行处理
                val result = processingManager.processAllImages(
                    subject = subject,
                    enhancedDir = enhancedDir,
                    splitDir = regionsDir,
                    scope = this
                )

                if (result.success) {
                    addProgressLog("✅ AI分析完成: ${result.totalAnalyzed} 张")

                    // 处理完成后重新加载JSON结果
                    loadJsonResults()
                    addProgressLog("📄 已刷新JSON结果")

                    // 稍等一下，等待上传完成
                    delay(2000)
                } else {
                    addProgressLog("❌ AI分析失败: ${result.message}")
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

    /**
     * 删除答案（本地和数据库）
     */
    private fun deleteAnswer(answerId: String, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🗑️  开始删除答案: $answerId")

                // 第一步：删除本地文件
                try {
                    val file = File(resultsDir, fileName)
                    if (file.exists()) {
                        file.delete()
                        Log.d(TAG, "✅ 本地答案文件已删除: $fileName")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️  删除本地文件失败: ${e.message}")
                }

                // 第二步：从数据库删除
                val config = ConfigManager.getConfig()
                val serverIp = config.server.ip
                val serverPort = config.server.port

                if (serverIp.isNotEmpty() && serverPort.isNotEmpty()) {
                    val url = "http://${serverIp}:${serverPort}/api/answer/$answerId"

                    val request = Request.Builder()
                        .url(url)
                        .delete()
                        .build()

                    try {
                        val response = okHttpClient.newCall(request).execute()
                        if (response.isSuccessful) {
                            Log.d(TAG, "✅ 数据库答案已删除: $answerId")
                            addProgressLog("✅ 答案已删除: $fileName")
                        } else {
                            Log.w(TAG, "⚠️  数据库删除失败: HTTP ${response.code}")
                            addProgressLog("⚠️  数据库删除失败，但本地已删除: $fileName")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️  删除数据库答案异常: ${e.message}")
                        addProgressLog("⚠️  网络删除失败，但本地已删除: $fileName")
                    }
                } else {
                    Log.w(TAG, "⚠️  服务器配置未设置，仅删除本地文件")
                    addProgressLog("⚠️  服务器配置未设置，仅删除本地文件")
                }

                // 第三步：刷新列表
                loadJsonResults()

            } catch (e: Exception) {
                Log.e(TAG, "❌ 删除答案异常: ${e.message}", e)
                addProgressLog("❌ 删除失败: ${e.message}")
            }
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