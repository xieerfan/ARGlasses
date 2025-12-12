package com.example.myapplication

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.myapplication.config.ConfigManager

/**
 * 图片AI分析器 - 使用ChatAnywhere API的Gemini模型
 *
 * ✅ 修复点：
 * - 使用ConfigManager读取API Key（和百度一致的方式）
 * - 改进错误处理和日志
 */
class ImageAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "ImageAnalyzer"
        private const val ENDPOINT = "https://api.chatanywhere.tech/v1/chat/completions"
        private const val MODEL = "gemini-2.5-pro"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 300L
        private const val WRITE_TIMEOUT = 300L
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()

    /**
     * 获取API Key - 使用ConfigManager（和百度方式一致）
     */
    private fun getApiKey(): String? {
        return try {
            val config = ConfigManager.getConfig()
            val apiKey = config.api.aiKey  // ✅ 使用aiKey（对应设置界面的aiKey字段）

            if (apiKey.isEmpty()) {
                Log.w(TAG, "⚠️  ConfigManager中没有配置aiKey")
                return null
            }

            Log.d(TAG, "✅ 成功获取AI API Key (长度: ${apiKey.length})")
            apiKey
        } catch (e: Exception) {
            Log.e(TAG, "❌ 从ConfigManager获取API Key失败: ${e.message}", e)
            null
        }
    }

    /**
     * 分析单张图片
     */
    suspend fun analyzeImage(
        imageFile: File,
        subject: String,
        imageIndex: Int,
        totalImages: Int
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📊 开始分析: $imageIndex/$totalImages - 科目: $subject")

            // ✅ 获取API Key（使用ConfigManager，和百度方式一致）
            val apiKey = getApiKey()
            if (apiKey.isNullOrEmpty()) {
                Log.e(TAG, "❌ AI_API_KEY未配置，请在设置中配置")
                return@withContext "错误：AI API Key未配置，请在设置中配置"
            }

            // 编码图片为Base64
            Log.d(TAG, "🖼️  编码图片为Base64...")
            val base64Image = encodeImageToBase64(imageFile)

            // 获取科目对应的提示词
            Log.d(TAG, "📝 获取科目提示词: $subject")
            val promptsManager = PromptsManager(context)
            if (!promptsManager.initialize()) {
                Log.w(TAG, "⚠️  PromptsManager初始化失败，使用默认提示词")
            }
            val prompt = promptsManager.getPromptForSubject(subject)

            // 构建请求
            Log.d(TAG, "🔨 构建API请求...")
            val requestBody = buildRequestBody(base64Image, prompt)

            val request = Request.Builder()
                .url(ENDPOINT)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .build()

            Log.d(TAG, "🚀 发送请求到: $ENDPOINT")

            // 执行请求
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "未知错误"
                    Log.e(TAG, "❌ API返回错误 [${response.code}]: $errorBody")
                    return@withContext "API错误: ${response.code} - $errorBody"
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "✅ 收到响应，长度: ${responseBody.length}")

                // 解析响应
                val result = parseResponse(responseBody)
                Log.d(TAG, "📤 分析完成: ${result.take(100)}...")
                return@withContext result
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 分析异常: ${e.message}", e)
            return@withContext "分析异常: ${e.message}"
        }
    }

    /**
     * 将图片文件编码为Base64字符串
     */
    private fun encodeImageToBase64(imageFile: File): String {
        val fileBytes = imageFile.readBytes()
        val base64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
        Log.d(TAG, "✅ 图片已编码: ${imageFile.name} (${fileBytes.size} bytes -> ${base64.length} chars)")
        return base64
    }

    /**
     * 构建API请求体
     */
    private fun buildRequestBody(base64Image: String, prompt: String): okhttp3.RequestBody {
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    // 文字部分
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    // 图片部分
                    put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64Image")
                            put("detail", "high")
                        })
                    })
                })
            })
        }

        val requestJson = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("max_tokens", 1000000)
            put("temperature", 0.7)
        }

        Log.d(TAG, "🔨 请求体构建完成，大小: ${requestJson.toString().length} bytes")
        return requestJson.toString().toRequestBody("application/json".toMediaType())
    }

    /**
     * 解析API响应
     */
    private fun parseResponse(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)

            if (!json.has("choices")) {
                Log.w(TAG, "⚠️  响应中没有choices字段")
                return "分析失败：响应格式错误"
            }

            val choices = json.getJSONArray("choices")
            if (choices.length() == 0) {
                Log.w(TAG, "⚠️  choices数组为空")
                return "分析失败：没有返回内容"
            }

            val choice = choices.getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = message.getString("content")

            Log.d(TAG, "✅ 成功解析响应内容，长度: ${content.length}")
            content
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析响应失败: ${e.message}", e)
            "解析错误: ${e.message}"
        }
    }
}