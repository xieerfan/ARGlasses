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
import com.example.myapplication.ImageProcessingManager
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
import com.example.myapplication.ImageEnhancer
import com.example.myapplication.ImageSplitter

/**
 * ✅ 修复：添加科目选择状态，BLE回调时使用UI选择的科目
 * ✅ 新增：JSON显示逻辑，AI生成JSON后只发送一次显示
 */
class AiProcessActivity : ComponentActivity() {

    companion object {
        private const val TAG = "AiProcessActivity"
        private const val DEFAULT_SUBJECT = "数学"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isTransferring = false

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

    // ✅ 新增：保存UI上选择的科目，BLE回调时使用这个值
    private val _selectedSubject = MutableStateFlow("physics")
    val selectedSubject: StateFlow<String> = _selectedSubject

    // ✅ 新增：标记是否已显示过JSON，确保只显示一次
    private var jsonDisplayed = false

    private var processingJob: Job? = null

    // ✅ 统一的目录定义
    private val baseDir by lazy { File(filesDir, "ai_process") }
    private val originalImagesDir by lazy { File(baseDir, "original") }
    private val enhancedDir by lazy { File(baseDir, "enhanced") }
    private val regionsDir by lazy { File(baseDir, "regions") }
    private val resultsDir by lazy { File(baseDir, "results") }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val requestImageRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isTransferring) {
                MainActivity.bleManager.readImageLength()
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "✨ Activity创建")

        // ✅ 初始化目录
        initializeDirectories()

        // ✅ 清空前次的数据
        clearPreviousData()

        imageEnhancer = ImageEnhancer(this)
        imageSplitter = ImageSplitter(this)
        processingManager = ImageProcessingManager(this)

        answerUploadManager = AnswerUploadManager(MainActivity.bleManager, NetworkManager)
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
                        onStartProcess = { subject ->
                            // ✅ 更新选择的科目
                            _selectedSubject.value = subject
                            // ✅ 重置JSON显示标志，准备显示新的JSON
                            jsonDisplayed = false
                            startProcessing(subject)
                        },
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
     * ✅ 初始化所有必要的目录
     */
    private fun initializeDirectories() {
        try {
            Log.d(TAG, "📁 初始化目录结构...")

            // 创建所有必要的目录
            listOf(baseDir, originalImagesDir, enhancedDir, regionsDir, resultsDir).forEach { dir ->
                if (!dir.exists()) {
                    val created = dir.mkdirs()
                    Log.d(TAG, "📁 ${dir.name}: ${if (created) "✅ 已创建" else "⚠️ 创建失败"}")
                    Log.d(TAG, "   路径: ${dir.absolutePath}")
                } else {
                    Log.d(TAG, "📁 ${dir.name}: ✅ 已存在")
                    Log.d(TAG, "   路径: ${dir.absolutePath}")
                }
            }

            Log.d(TAG, "✅ 目录初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 目录初始化失败: ${e.message}", e)
        }
    }

    /**
     * ✅ 清空前次的数据
     */
    private fun clearPreviousData() {
        try {
            Log.d(TAG, "🧹 清空前次的数据...")

            receivedImages.clear()
            enhancedImages.clear()
            splitImages.clear()
            receiveProgress.clear()

            _receivedImagesCount.value = 0
            _enhancedImagesCount.value = 0
            _progressLogsCount.value = 0

            // 删除原始图片
            if (originalImagesDir.exists()) {
                originalImagesDir.deleteRecursively()
                Log.d(TAG, "✅ 已清空原始图片目录")
            }

            // 删除增强后的图片
            if (enhancedDir.exists()) {
                enhancedDir.deleteRecursively()
                Log.d(TAG, "✅ 已清空增强图片目录")
            }

            // 删除分割的区域
            if (regionsDir.exists()) {
                regionsDir.deleteRecursively()
                Log.d(TAG, "✅ 已清空区域目录")
            }

            // 重新创建空目录
            listOf(originalImagesDir, enhancedDir, regionsDir).forEach { dir ->
                dir.mkdirs()
            }

            Log.d(TAG, "✅ 前次数据清空完成")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 清空前次数据失败: ${e.message}", e)
        }
    }

    private fun loadJsonResults() {
        try {
            jsonResults.clear()

            if (resultsDir.exists() && resultsDir.isDirectory) {
                resultsDir.listFiles { file ->
                    file.extension == "json"
                }?.forEach { jsonFile ->
                    if (!jsonResults.contains(jsonFile)) {
                        jsonResults.add(jsonFile)
                        Log.d(TAG, "📄 加载JSON结果: ${jsonFile.name}")
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
        // 监听 BLE 连接状态
        lifecycleScope.launch {
            MainActivity.bleManager.isConnected.collect { isConnected ->
                _isBleConnected.value = isConnected
                if (isConnected) {
                    addProgressLog("🔗 BLE已连接")
                } else {
                    addProgressLog("⚠️ BLE已断开")
                }
            }
        }

        // 监听接收到的图片数据
        lifecycleScope.launch {
            MainActivity.bleManager.receivedImage.collect { imageData ->
                imageData?.let {
                    saveReceivedImage(it)
                    isTransferring = false
                    addProgressLog("✅ 图片接收完成")
                }
            }
        }

        // 监听 AI 工作命令 - ✅ 修复：使用UI选择的科目
        lifecycleScope.launch {
            MainActivity.bleManager.aiWorkCommand.collect { shouldProcess ->
                if (shouldProcess) {
                    // ✅ 获取当前UI选择的科目
                    val currentSubject = _selectedSubject.value
                    Log.d(TAG, "🤖 检测到AI工作命令，准备启动处理，科目: $currentSubject")
                    addProgressLog("🤖 设备发送AI工作命令，准备启动处理，科目: $currentSubject")

                    delay(500)

                    if (receivedImages.isNotEmpty()) {
                        addProgressLog("📸 发现${receivedImages.size}张图片，开始处理...")
                        // ✅ 使用UI选择的科目，不是硬编码的"数学"
                        startProcessing(currentSubject)
                    } else {
                        addProgressLog("⚠️ 没有接收到图片，请先上传图片")
                    }
                }
            }
        }
    }

    /**
     * ✅ 保存接收到的图片 - 修复版本
     */
    private fun saveReceivedImage(imageData: ByteArray) {
        try {
            // 确保目录存在
            if (!originalImagesDir.exists()) {
                originalImagesDir.mkdirs()
                Log.d(TAG, "📁 创建原始图片目录: ${originalImagesDir.absolutePath}")
            }

            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val file = File(originalImagesDir, fileName)

            // 保存文件
            FileOutputStream(file).use { output ->
                output.write(imageData)
                output.flush()
            }

            // 验证文件是否成功保存
            if (file.exists() && file.length() > 0) {
                receivedImages.add(file)
                _receivedImagesCount.value = receivedImages.size

                Log.d(TAG, "✅ 图片保存成功")
                Log.d(TAG, "   文件名: $fileName")
                Log.d(TAG, "   文件大小: ${file.length()} 字节")
                Log.d(TAG, "   完整路径: ${file.absolutePath}")
                Log.d(TAG, "   共接收: ${receivedImages.size} 张")

                addProgressLog("📷 接收图片: $fileName (共${receivedImages.size}张)")
            } else {
                Log.e(TAG, "❌ 文件保存失败，文件不存在或大小为0")
                addProgressLog("❌ 图片保存失败: 文件验证失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存图片异常: ${e.message}", e)
            addProgressLog("❌ 保存失败: ${e.message}")
        }
    }

    /**
     * ✅ 拍照后保存 - 修复版本
     */
    private fun addPhotoToReceivedImages(photoFile: File) {
        try {
            // 确保目录存在
            if (!originalImagesDir.exists()) {
                originalImagesDir.mkdirs()
                Log.d(TAG, "📁 创建原始图片目录: ${originalImagesDir.absolutePath}")
            }

            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            val copiedFile = File(originalImagesDir, fileName)

            // 复制文件
            photoFile.copyTo(copiedFile, overwrite = true)

            // 验证文件是否成功复制
            if (copiedFile.exists() && copiedFile.length() > 0) {
                receivedImages.add(copiedFile)
                _receivedImagesCount.value = receivedImages.size

                Log.d(TAG, "✅ 拍照保存成功")
                Log.d(TAG, "   源文件: ${photoFile.absolutePath}")
                Log.d(TAG, "   目标文件: ${copiedFile.absolutePath}")
                Log.d(TAG, "   文件大小: ${copiedFile.length()} 字节")
                Log.d(TAG, "   共接收: ${receivedImages.size} 张")

                addProgressLog("📸 拍照上传: $fileName (共${receivedImages.size}张)")
            } else {
                Log.e(TAG, "❌ 拍照保存失败，文件不存在或大小为0")
                addProgressLog("❌ 拍照保存失败: 文件验证失败")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 拍照保存异常: ${e.message}", e)
            addProgressLog("❌ 拍照失败: ${e.message}")
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

                // ✅ 新增：处理过程中停止图片接收
                handler.removeCallbacks(requestImageRunnable)
                addProgressLog("⏸️  已暂停图片接收，开始处理...")
                Log.d(TAG, "⏸️  已停止 requestImageRunnable")

                addProgressLog("🎬 开始处理流程，科目: $subject")

                // ✅ 记录接收到的图片
                Log.d(TAG, "📊 处理图片统计:")
                Log.d(TAG, "   接收图片数: ${receivedImages.size}")
                Log.d(TAG, "   原始图片目录: ${originalImagesDir.absolutePath}")
                Log.d(TAG, "   增强图片目录: ${enhancedDir.absolutePath}")
                Log.d(TAG, "   区域目录: ${regionsDir.absolutePath}")

                // 验证原始图片目录
                if (!originalImagesDir.exists()) {
                    Log.e(TAG, "❌ 原始图片目录不存在!")
                    addProgressLog("❌ 错误: 原始图片目录不存在")
                    _isProcessing.value = false
                    return@launch
                }

                val imageFiles = originalImagesDir.listFiles() ?: emptyArray()
                Log.d(TAG, "📁 原始图片目录中找到 ${imageFiles.size} 个文件")
                imageFiles.forEach { file ->
                    Log.d(TAG, "   - ${file.name} (${file.length()} 字节)")
                }

                processingManager.setAnalysisCallback { title, message ->
                    addProgressLog("$title: $message")
                }

                // ✅ 修改：重置JSON显示标志，准备显示第一个生成的JSON
                jsonDisplayed = false

                // ✅ 修改：设置JSON发送回调 - 只显示第一个生成的JSON
                processingManager.setJsonSendCallback { jsonFile ->
                    // ✅ 关键：只显示第一个JSON，之后生成的JSON不再显示
                    if (!jsonDisplayed) {
                        Log.d(TAG, "📊 第一个JSON已生成，将显示此JSON")
                        addProgressLog("📊 第一个JSON已生成，准备显示...")
                        sendJsonForDisplayOnce(jsonFile)
                        jsonDisplayed = true  // ✅ 标记已显示，后续不再显示
                    } else {
                        Log.d(TAG, "📊 后续JSON已生成，不再显示（仅显示第一个）")
                        addProgressLog("📊 后续JSON已生成（仅显示第一个）")
                    }
                }

                val result = processingManager.processAllImages(
                    subject = subject,
                    enhancedDir = enhancedDir,
                    splitDir = regionsDir,
                    scope = this
                )

                if (result.success) {
                    addProgressLog("✅ AI分析完成: ${result.totalAnalyzed} 张")
                    loadJsonResults()
                    addProgressLog("📄 已刷新JSON结果")
                    delay(2000)
                } else {
                    addProgressLog("❌ AI分析失败: ${result.message}")
                }

                addProgressLog("✅ 处理流程完成")
                _isProcessing.value = false

                // ✅ 新增：处理完成后恢复图片接收
                delay(500)
                handler.post(requestImageRunnable)
                addProgressLog("▶️  已恢复图片接收")
                Log.d(TAG, "▶️  已重启 requestImageRunnable")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理异常: ${e.message}", e)
                addProgressLog("❌ 处理异常: ${e.message}")
                _isProcessing.value = false

                // ✅ 异常时也要恢复图片接收
                handler.post(requestImageRunnable)
                addProgressLog("▶️  异常处理后已恢复图片接收")
            }
        }
    }

    /**
     * ✅ 新增：发送JSON到ESP32并显示（只发送一次）
     *
     * 流程：
     * 1. 发送文件名 /an/xxx.json 到特征1_3
     * 2. 发送start到特征1_2
     * 3. 分块发送JSON内容到特征1_1
     * 4. 发送end到特征1_2
     * 5. 发送display_json命令到特征3_2
     */
    private fun sendJsonForDisplayOnce(jsonFile: File) {
        try {
            Log.d(TAG, "📤 开始发送JSON到ESP32显示: ${jsonFile.name}")
            addProgressLog("📤 正在发送JSON到设备显示...")

            // 读取JSON内容
            val jsonContent = jsonFile.readText(Charsets.UTF_8)
            Log.d(TAG, "📋 JSON内容长度: ${jsonContent.length} 字符")

            // ✅ 使用新方法发送JSON并显示
            MainActivity.bleManager.sendJsonForDisplay(jsonContent)

            Log.d(TAG, "✅ JSON已发送到ESP32")
            addProgressLog("📤 JSON结果已发送到设备并显示")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送JSON异常: ${e.message}", e)
            addProgressLog("❌ 发送JSON失败: ${e.message}")
        }
    }

    private fun deleteAllImages() {
        try {
            // 删除基础目录下的所有图片
            listOf(originalImagesDir, enhancedDir, regionsDir).forEach { dir ->
                if (dir.exists()) {
                    dir.deleteRecursively()
                    Log.d(TAG, "✅ 已删除目录: ${dir.absolutePath}")
                }
            }

            receivedImages.clear()
            enhancedImages.clear()
            splitImages.clear()
            _receivedImagesCount.value = 0
            _enhancedImagesCount.value = 0

            // 重新创建空目录
            listOf(originalImagesDir, enhancedDir, regionsDir).forEach { dir ->
                dir.mkdirs()
            }

            addProgressLog("🗑️  已删除所有图片")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 删除图片失败: ${e.message}", e)
            addProgressLog("❌ 删除失败: ${e.message}")
        }
    }

    /**
     * ✅ 删除答案（本地和数据库）
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
        Log.d(TAG, "🗑️  Activity销毁")
        isRunning = false
        handler.removeCallbacks(requestImageRunnable)
        processingJob?.cancel()
        super.onDestroy()
    }
}