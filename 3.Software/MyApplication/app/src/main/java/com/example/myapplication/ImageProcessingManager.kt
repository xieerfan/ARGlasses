package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File

/**
 * ✅ 图片处理管理器
 *
 * 功能：整合所有图片处理步骤
 * 对应Python的Image_process函数
 *
 * 处理流程：
 * 1. 图片增强（所有科目都需要）
 * 2. 图片分割（英文和中文不需要）
 * 3. AI分析（根据科目使用对应的提示词）
 * 4. 保存结果
 */
class ImageProcessingManager(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessingManager"

        // 输出目录路径
        private const val OUTPUT_DIR = "analysis_results"
    }

    private val imageEnhancer = ImageEnhancer(context)
    private val imageSplitter = ImageSplitter(context)
    private val imageAnalyzer = ImageAnalyzer(context)
    private val promptsManager = PromptsManager(context)

    private var analysisCallback: ((String, String) -> Unit)? = null

    /**
     * 初始化管理器
     */
    fun initialize(): Boolean {
        return try {
            // 初始化提示词管理器
            if (!promptsManager.initialize()) {
                Log.w(TAG, "⚠️  提示词加载失败，将使用默认提示词")
            }

            // ✅ ImageAnalyzer不需要初始化，直接使用即可
            Log.d(TAG, "✅ 处理管理器初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败: ${e.message}", e)
            false
        }
    }

    /**
     * 设置分析进度回调
     */
    fun setAnalysisCallback(callback: (String, String) -> Unit) {
        this.analysisCallback = callback
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

            if (!enhancedDir.exists() || enhancedDir.listFiles()?.isEmpty() != false) {
                Log.e(TAG, "❌ 增强后图片目录不存在或为空")
                result.success = false
                result.message = "❌ 增强后图片目录不存在或为空"
                return@withContext result
            }

            result.enhancedImages = enhancedDir.listFiles()?.filter {
                it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png")
            } ?: emptyList()

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
                    // 执行分割（实际上图片应该已经在splitDir中）
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
            val imagesToAnalyze = if (subject.lowercase() in listOf("english", "chinese")) {
                // 英文和中文直接分析增强后的图片
                result.enhancedImages
            } else {
                // 其他科目分析分割后的图片
                if (splitDir != null && splitDir.exists()) {
                    collectAllSplitImages(splitDir)
                } else {
                    result.enhancedImages
                }
            }

            Log.d(TAG, "📊 准备分析 ${imagesToAnalyze.size} 张图片")

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

                    // 保存单个结果
                    saveAnalysisResult(
                        filename = "${index + 1}.jpg",
                        result = analysisResult,
                        subject = subject,
                        imageIndex = index + 1,
                        totalImages = imagesToAnalyze.size
                    )

                    if (!analysisResult.startsWith("❌")) {
                        successCount++
                    }

                    result.analyzedImages.add(
                        AnalyzedImage(
                            filename = imageFile.name,
                            subject = subject,
                            result = analysisResult
                        )
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "❌ 分析图片 ${imageFile.name} 失败: ${e.message}", e)
                    result.analyzedImages.add(
                        AnalyzedImage(
                            filename = imageFile.name,
                            subject = subject,
                            result = "❌ 分析失败: ${e.message}"
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
        totalImages: Int
    ) {
        try {
            // 获取输出目录
            val outputDir = File(context.filesDir, OUTPUT_DIR)
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

        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存结果失败: ${e.message}", e)
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