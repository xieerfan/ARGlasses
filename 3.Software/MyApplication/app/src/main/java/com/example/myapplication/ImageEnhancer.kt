package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 百度图片增强管理器
 * 实现图片OCR增强功能
 */
class ImageEnhancer(private val context: Context) {

    companion object {
        private const val TAG = "ImageEnhancer"
        private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        private const val ENHANCE_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/doc_crop_enhance"
    }

    // 增强进度状态
    data class EnhanceProgress(
        val totalImages: Int = 0,
        val currentIndex: Int = 0,
        val currentFileName: String = "",
        val isEnhancing: Boolean = false,
        val isComplete: Boolean = false,
        val errorMessage: String? = null
    )

    private val _enhanceProgress = MutableStateFlow<EnhanceProgress?>(null)
    val enhanceProgress: StateFlow<EnhanceProgress?> = _enhanceProgress

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0

    /**
     * 获取百度API访问令牌
     */
    private suspend fun getBaiduToken(): String? = withContext(Dispatchers.IO) {
        try {
            // 检查token是否过期
            if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
                Log.d(TAG, "使用缓存的access_token")
                return@withContext accessToken
            }

            // 从配置获取API密钥
            val config = ConfigManager.getConfig()
            val apiKey = config.api.baiduApiKey
            val secretKey = config.api.baiduSecretKey

            if (apiKey.isEmpty() || secretKey.isEmpty()) {
                Log.e(TAG, "百度API密钥未配置")
                return@withContext null
            }

            Log.d(TAG, "开始获取百度API访问令牌...")

            val url = "$TOKEN_URL?grant_type=client_credentials&client_id=$apiKey&client_secret=$secretKey"

            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody())
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)
                accessToken = jsonResponse.optString("access_token")
                val expiresIn = jsonResponse.optInt("expires_in", 2592000) // 默认30天
                tokenExpireTime = System.currentTimeMillis() + (expiresIn * 1000L)

                Log.d(TAG, "百度API访问令牌获取成功，有效期: ${expiresIn}秒")
                return@withContext accessToken
            } else {
                Log.e(TAG, "获取访问令牌失败: HTTP ${response.code}, $responseBody")
                return@withContext null
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取百度访问令牌异常: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 增强单张图片
     */
    private suspend fun enhanceSingleImage(
        imageFile: File,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始增强图片: ${imageFile.name}")

            // 获取访问令牌
            val token = getBaiduToken()
            if (token == null) {
                Log.e(TAG, "无法获取访问令牌")
                return@withContext false
            }

            // 读取图片并转为base64
            val imageBytes = imageFile.readBytes()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            Log.d(TAG, "图片大小: ${imageBytes.size} 字节, Base64长度: ${imageBase64.length}")

            // 构建请求
            val url = "$ENHANCE_URL?access_token=$token"

            val jsonBody = JSONObject().apply {
                put("image", imageBase64)
                put("scan_type", 3)      // 3表示自动检测
                put("enhance_type", 3)   // 3表示去除阴影、增强对比度
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "发送增强请求到百度API...")

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)

                // 检查是否有错误
                if (jsonResponse.has("error_code")) {
                    val errorCode = jsonResponse.getInt("error_code")
                    val errorMsg = jsonResponse.optString("error_msg", "未知错误")
                    Log.e(TAG, "百度API返回错误: $errorCode - $errorMsg")
                    return@withContext false
                }

                // 获取增强后的图片
                if (jsonResponse.has("image_processed")) {
                    val processedBase64 = jsonResponse.getString("image_processed")
                    val processedBytes = Base64.decode(processedBase64, Base64.DEFAULT)

                    // 保存增强后的图片
                    FileOutputStream(outputFile).use { fos ->
                        fos.write(processedBytes)
                    }

                    Log.d(TAG, "图片增强成功: ${imageFile.name} -> ${outputFile.name}")
                    return@withContext true
                } else {
                    Log.e(TAG, "响应中没有增强后的图片数据")
                    return@withContext false
                }
            } else {
                Log.e(TAG, "增强请求失败: HTTP ${response.code}, $responseBody")
                return@withContext false
            }

        } catch (e: Exception) {
            Log.e(TAG, "增强图片异常: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 批量增强图片
     * @param imageFiles 要增强的图片文件列表
     * @param outputDir 输出目录
     */
    suspend fun enhanceImages(
        imageFiles: List<File>,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val enhancedFiles = mutableListOf<File>()

        if (imageFiles.isEmpty()) {
            Log.w(TAG, "没有图片需要增强")
            return@withContext enhancedFiles
        }

        // 确保输出目录存在
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        Log.d(TAG, "开始批量增强 ${imageFiles.size} 张图片")

        _enhanceProgress.value = EnhanceProgress(
            totalImages = imageFiles.size,
            currentIndex = 0,
            isEnhancing = true
        )

        var successCount = 0

        for ((index, imageFile) in imageFiles.withIndex()) {
            _enhanceProgress.value = EnhanceProgress(
                totalImages = imageFiles.size,
                currentIndex = index + 1,
                currentFileName = imageFile.name,
                isEnhancing = true
            )

            try {
                // 生成输出文件名
                val outputFile = File(outputDir, "enhanced_${imageFile.name}")

                // 增强图片
                val success = enhanceSingleImage(imageFile, outputFile)

                if (success) {
                    enhancedFiles.add(outputFile)
                    successCount++
                    Log.d(TAG, "进度: ${index + 1}/${imageFiles.size} - 增强成功")
                } else {
                    Log.e(TAG, "进度: ${index + 1}/${imageFiles.size} - 增强失败")
                }

                // 添加延迟避免API请求过快
                delay(500)

            } catch (e: Exception) {
                Log.e(TAG, "处理图片 ${imageFile.name} 时出错: ${e.message}", e)
            }
        }

        Log.d(TAG, "批量增强完成: 成功 $successCount/${imageFiles.size}")

        _enhanceProgress.value = EnhanceProgress(
            totalImages = imageFiles.size,
            currentIndex = imageFiles.size,
            isEnhancing = false,
            isComplete = true
        )

        // 2秒后清空进度
        delay(2000)
        _enhanceProgress.value = null

        return@withContext enhancedFiles
    }

    /**
     * 增强单个Bitmap
     */
    suspend fun enhanceBitmap(bitmap: Bitmap): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始增强Bitmap...")

            // 获取访问令牌
            val token = getBaiduToken()
            if (token == null) {
                Log.e(TAG, "无法获取访问令牌")
                return@withContext null
            }

            // Bitmap转Base64
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val imageBytes = baos.toByteArray()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            // 构建请求
            val url = "$ENHANCE_URL?access_token=$token"

            val jsonBody = JSONObject().apply {
                put("image", imageBase64)
                put("scan_type", 3)
                put("enhance_type", 3)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)

                if (jsonResponse.has("error_code")) {
                    Log.e(TAG, "百度API返回错误: ${jsonResponse.getString("error_msg")}")
                    return@withContext null
                }

                if (jsonResponse.has("image_processed")) {
                    val processedBase64 = jsonResponse.getString("image_processed")
                    val processedBytes = Base64.decode(processedBase64, Base64.DEFAULT)
                    val enhancedBitmap = BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size)

                    Log.d(TAG, "Bitmap增强成功")
                    return@withContext enhancedBitmap
                }
            }

            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "增强Bitmap异常: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * 清空进度状态
     */
    fun clearProgress() {
        _enhanceProgress.value = null
    }
}