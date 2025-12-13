package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import com.example.myapplication.network.AnswerUploadManager

/**
 * ✅ 修改：图片处理管理器 - 添加答案上传功能
 *
 * 功能：整合所有图片处理步骤
 * 对应Python的Image_process函数
 *
 * 处理流程：
 * 1. 图片增强（所有科目都需要）
 * 2. 图片分割（英文和中文不需要）
 * 3. AI分析（根据科目使用对应的提示词）
 * 4. 保存结果
 * 5. ✅ 新增：上传答案到BLE设备和服务器
 */
class ImageProcessingManager(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessingManager"
        private const val OUTPUT_DIR = "analysis_results"
    }

    private val imageEnhancer = ImageEnhancer(context)
    private val imageSplitter = ImageSplitter(context)
    private val imageAnalyzer = ImageAnalyzer(context)
    private val promptsManager = PromptsManager(context)

    private var analysisCallback: ((String, String) -> Unit)? = null
    private var answerUploadManager: AnswerUploadManager? = null
    // ✅ 新增：JSON发送回调
    private var jsonSendCallback: ((File) -> Unit)? = null

    /**
     * 初始化管理器
     */
    fun initialize(): Boolean {
        return try {
            // 初始化提示词管理器
            if (!promptsManager.initialize()) {
                Log.w(TAG, "⚠️  提示词加载失败，将使用默认提示词")
            }

            Log.d(TAG, "✅ 处理管理器初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败: ${e.message}", e)
            false
        }
    }

    /**
     * 设置答案上传管理器（用于上传答案到BLE和服务器）
     */
    fun setAnswerUploadManager(manager: AnswerUploadManager) {
        this.answerUploadManager = manager
    }

    /**
     * 设置分析进度回调
     */
    fun setAnalysisCallback(callback: (String, String) -> Unit) {
        this.analysisCallback = callback
    }

    /**
     * ✅ 新增：设置JSON发送回调（用于发送结果到ESP32）
     */
    fun setJsonSendCallback(callback: (File) -> Unit) {
        this.jsonSendCallback = callback
    }

    /**
     * 处理所有图片（主流程）
     *
     * @param subject 科目名称（不再自动检测，必须手动指定）
     * @param scope Coroutine作用域
     */
    suspend fun processAllImages(
        subject: String,
        enhancedDir: File? = null,
        splitDir: File? = null,
        scope: CoroutineScope = GlobalScope
    ): ProcessingResult = withContext(Dispatchers.IO) {
        val result = ProcessingResult(subject = subject)

        try {
            Log.d(TAG, "🎬 开始图片处理流程，科目: $subject")
            result.startTime = System.currentTimeMillis()

            // ========== 第一步：图片增强（所有科目都需要） ==========
            Log.d(TAG, "📸 第一步：图片增强处理...")
            result.message = "📸 正在增强图片..."
            notifyProgress("进度", result.message)

            if (enhancedDir == null) {
                Log.e(TAG, "❌ 增强后图片目录为空")
                result.success = false
                result.message = "❌ 增强后图片目录为空"
                return@withContext result
            }

            // ✅ 确保增强目录存在
            if (!enhancedDir.exists()) {
                enhancedDir.mkdirs()
                Log.d(TAG, "📁 已创建增强目录: ${enhancedDir.absolutePath}")
            }

            // ✅ 获取原始图片
            val originalImagesDir = File(context.filesDir, "ai_process/original")
            val originalImages = originalImagesDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png")
            }?.sortedBy { it.name } ?: emptyList()

            if (originalImages.isEmpty()) {
                Log.e(TAG, "❌ 没有原始图片需要增强")
                result.success = false
                result.message = "❌ 没有原始图片需要增强"
                return@withContext result
            }

            Log.d(TAG, "🚀 开始调用增强器处理 ${originalImages.size} 张图片...")

            // ✅ 调用增强器生成增强后的图片
            result.enhancedImages = imageEnhancer.enhanceImages(originalImages, enhancedDir)

            if (result.enhancedImages.isEmpty()) {
                Log.e(TAG, "❌ 图片增强失败，没有输出结果")
                result.success = false
                result.message = "❌ 图片增强失败"
                return@withContext result
            }

            Log.d(TAG, "✅ 增强完成: ${result.enhancedImages.size} 张图片")

            // ========== 第二步：图片分割（根据科目决定） ==========
            if (subject.lowercase() in listOf("english", "chinese", "order")) {
                Log.d(TAG, "⏭️  科目 $subject：跳过图片分割步骤")
                result.message = "⏭️  英语/中文科目：不进行分割"
            } else {
                Log.d(TAG, "🔄 第二步：图片分割处理...")
                result.message = "🔄 正在分割图片..."
                notifyProgress("进度", result.message)

                if (splitDir == null) {
                    Log.w(TAG, "⚠️  分割后图片目录为空，跳过分割")
                } else {
                    // ✅ 实际调用分割器处理增强后的图片
                    if (!splitDir.exists()) {
                        splitDir.mkdirs()
                        Log.d(TAG, "📁 已创建分割目录: ${splitDir.absolutePath}")
                    }

                    Log.d(TAG, "🚀 开始调用分割器处理 ${result.enhancedImages.size} 张图片...")

                    // 逐张分割
                    result.enhancedImages.forEachIndexed { index, imageFile ->
                        try {
                            val progress = "🔄 图片分割中 (${index + 1}/${result.enhancedImages.size}): ${imageFile.name}"
                            Log.d(TAG, progress)
                            result.message = progress
                            notifyProgress("进度", progress)

                            // 为每张图片创建单独的分割目录
                            val imageSplitDir = File(splitDir, "image_${index + 1}")
                            imageSplitDir.mkdirs()

                            // 调用分割器
                            val splitFiles = imageSplitter.splitImage(imageFile, imageSplitDir)

                            if (splitFiles.isNotEmpty()) {
                                Log.d(TAG, "✅ 图片 ${index + 1} 分割成功: ${splitFiles.size} 个区域")
                            } else {
                                Log.w(TAG, "⚠️  图片 ${index + 1} 分割失败或无法分割，将使用原图进行分析")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 分割图片 ${imageFile.name} 异常: ${e.message}", e)
                        }
                    }

                    Log.d(TAG, "✅ 图片分割处理完成")
                }
            }

            // ========== 第三步：跳过order科目 ==========
            if (subject.lowercase() == "order") {
                Log.d(TAG, "⏭️  科目为order，跳过AI分析")
                result.message = "⏭️  科目为order，跳过处理"
                result.success = true
                result.totalAnalyzed = 0
                return@withContext result
            }

            // ========== 第四步：AI分析 ==========
            Log.d(TAG, "🤖 第三步：AI分析处理...")
            result.message = "🤖 正在进行AI分析..."
            notifyProgress("进度", result.message)

            // 获取要分析的图片列表
            var imagesToAnalyze: List<File>

            if (subject.lowercase() in listOf("english", "chinese")) {
                // 英文和中文直接分析增强后的图片
                Log.d(TAG, "📌 英文/中文科目：直接分析增强后的图片")
                imagesToAnalyze = result.enhancedImages
            } else {
                // 其他科目优先分析分割后的图片
                Log.d(TAG, "📌 其他科目：优先使用分割后的图片")

                imagesToAnalyze = if (splitDir != null && splitDir.exists()) {
                    val splitImages = collectAllSplitImages(splitDir)
                    if (splitImages.isNotEmpty()) {
                        Log.d(TAG, "✅ 找到 ${splitImages.size} 张分割后的图片")
                        splitImages
                    } else {
                        Log.w(TAG, "⚠️  没有找到分割后的图片，使用增强后的图片作为 fallback")
                        result.enhancedImages
                    }
                } else {
                    Log.w(TAG, "⚠️  分割目录不存在或为空，使用增强后的图片作为 fallback")
                    result.enhancedImages
                }
            }

            Log.d(TAG, "📊 准备分析 ${imagesToAnalyze.size} 张图片")

            if (imagesToAnalyze.isEmpty()) {
                Log.e(TAG, "❌ 没有图片需要分析")
                result.success = false
                result.message = "❌ 没有图片需要分析"
                return@withContext result
            }

            // 获取输出目录
            val outputDir = File(context.filesDir, OUTPUT_DIR)
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 逐张分析
            var successCount = 0
            imagesToAnalyze.forEachIndexed { index, imageFile ->
                try {
                    val progress = "🤖 AI分析中 (${index + 1}/${imagesToAnalyze.size}): ${imageFile.name}"
                    Log.d(TAG, progress)
                    result.message = progress
                    notifyProgress("进度", progress)

                    // 调用分析器
                    val analysisResult = imageAnalyzer.analyzeImage(
                        imageFile,
                        subject,
                        index + 1,
                        imagesToAnalyze.size
                    )

                    if (!analysisResult.startsWith("❌")) {
                        successCount++
                        Log.d(TAG, "✅ 分析成功 (${index + 1}/${imagesToAnalyze.size})")

                        // 保存单个结果
                        val jsonFile = saveAnalysisResult(
                            filename = "${index + 1}.jpg",
                            result = analysisResult,
                            subject = subject,
                            imageIndex = index + 1,
                            totalImages = imagesToAnalyze.size,
                            outputDir = outputDir
                        )

                        // ✅ 新增：发送JSON结果到ESP32
                        if (jsonFile != null) {
                            Log.d(TAG, "📤 准备发送JSON到ESP32: ${jsonFile.name}")
                            try {
                                jsonSendCallback?.invoke(jsonFile)
                                Log.d(TAG, "📤 JSON已发送到ESP32")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 发送JSON到ESP32失败: ${e.message}", e)
                            }
                        }

                        // ✅ 新增：上传答案到BLE和服务器
                        if (jsonFile != null && answerUploadManager != null) {
                            uploadAnswerToDeviceAndServer(
                                jsonFile,
                                subject,
                                index + 1,
                                imagesToAnalyze.size
                            )
                        }
                    } else {
                        Log.e(TAG, "❌ 分析失败 (${index + 1}/${imagesToAnalyze.size}): $analysisResult")
                    }

                    result.analyzedImages.add(
                        AnalyzedImage(
                            filename = imageFile.name,
                            subject = subject,
                            result = analysisResult
                        )
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 分析图片 ${imageFile.name} 异常: ${e.message}", e)
                    result.analyzedImages.add(
                        AnalyzedImage(
                            filename = imageFile.name,
                            subject = subject,
                            result = "❌ 分析异常: ${e.message}"
                        )
                    )
                }
            }

            result.totalAnalyzed = successCount
            result.success = true
            result.message = "✅ 处理完成: 成功分析 $successCount/${imagesToAnalyze.size} 张图片"
            Log.d(TAG, result.message)

        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理流程异常: ${e.message}", e)
            result.success = false
            result.message = "❌ 处理流程异常: ${e.message}"
        } finally {
            result.endTime = System.currentTimeMillis()
            result.duration = result.endTime - result.startTime
        }

        return@withContext result
    }

    /**
     * ✅ 新增：上传答案到BLE设备和服务器
     */
    private fun uploadAnswerToDeviceAndServer(
        jsonFile: File,
        subject: String,
        imageIndex: Int,
        totalImages: Int
    ) {
        try {
            Log.d(TAG, "📤 开始上传答案: ${jsonFile.name}")

            if (answerUploadManager == null) {
                Log.w(TAG, "⚠️  AnswerUploadManager未初始化，无法上传")
                return
            }

            // 调用AnswerUploadManager进行上传
            answerUploadManager!!.uploadAnswer(
                jsonFile = jsonFile,
                subject = subject,
                imageIndex = imageIndex,
                totalImages = totalImages
            )

            notifyProgress("上传", "📤 答案正在上传到BLE和服务器...")
            Log.d(TAG, "✅ 答案上传已启动: ${jsonFile.name}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 上传答案异常: ${e.message}", e)
        }
    }

    /**
     * 收集分割后目录中的所有图片
     */
    private fun collectAllSplitImages(splitDir: File): List<File> {
        val images = mutableListOf<File>()

        splitDir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory) {
                subDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png")) {
                        images.add(file)
                    }
                }
            }
        }

        images.sortBy { it.name }
        Log.d(TAG, "📂 收集到 ${images.size} 张分割后的图片")
        return images
    }

    /**
     * 保存单个分析结果
     */
    private fun saveAnalysisResult(
        filename: String,
        result: String,
        subject: String,
        imageIndex: Int,
        totalImages: Int,
        outputDir: File
    ): File? {
        return try {
            // 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 保存为JSON文件
            val jsonFilename = "$imageIndex.jpg.json"
            val jsonFile = File(outputDir, jsonFilename)

            val resultData = mapOf(
                "question_id" to "$imageIndex.jpg",
                "subject" to subject,
                "total_questions" to totalImages,
                "current_index" to imageIndex,
                "analysis_result" to result
            )

            // 转换为JSON并保存
            val jsonContent = org.json.JSONObject(resultData).toString(2)
            jsonFile.writeText(jsonContent, Charsets.UTF_8)

            Log.d(TAG, "💾 结果已保存: $jsonFilename")
            jsonFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存结果失败: ${e.message}", e)
            null
        }
    }

    /**
     * 通知进度
     */
    private fun notifyProgress(title: String, message: String) {
        analysisCallback?.invoke(title, message)
    }

    /**
     * 获取支持的科目列表
     */
    fun getSupportedSubjects(): List<String> {
        return promptsManager.getSupportedSubjects()
    }

    /**
     * 获取科目的中文名称
     */
    fun getSubjectChinese(subject: String): String {
        return promptsManager.getSubjectChinese(subject)
    }
}

/**
 * 处理结果数据类
 */
data class ProcessingResult(
    val subject: String,
    var success: Boolean = false,
    var message: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var duration: Long = 0,
    var totalAnalyzed: Int = 0,
    var enhancedImages: List<File> = emptyList(),
    val analyzedImages: MutableList<AnalyzedImage> = mutableListOf()
)

/**
 * 已分析图片数据类
 */
data class AnalyzedImage(
    val filename: String,
    val subject: String,
    val result: String
)