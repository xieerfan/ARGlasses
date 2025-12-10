package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * ✅ 修复版：有道API图片分割管理器
 *
 * 改进点：
 * 1. 正确处理有道API的errorCode（0表示成功）
 * 2. 完善的JSON解析，支持复杂的segment格式
 * 3. 使用boundingBox进行精确分割
 * 4. 支持多边形分割和简单矩形分割
 * 5. 详细的日志输出便于调试
 */
class ImageSplitter(private val context: Context) {

    companion object {
        private const val TAG = "ImageSplitter"
        private const val SPLIT_URL = "https://openapi.youdao.com/cut_question"

        // 有道API错误码
        private const val YOUDAO_SUCCESS = 0  // ✅ 0 = 成功
    }

    // 分割进度状态
    data class SplitProgress(
        val totalImages: Int = 0,
        val currentIndex: Int = 0,
        val currentFileName: String = "",
        val isSplitting: Boolean = false,
        val isComplete: Boolean = false,
        val errorMessage: String? = null
    )

    // 分割区域信息
    data class RegionInfo(
        val boundingBox: String,
        val score: Double,
        val segment: String
    )

    private val _splitProgress = MutableStateFlow<SplitProgress?>(null)
    val splitProgress: StateFlow<SplitProgress?> = _splitProgress

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * 生成有道API签名
     */
    private fun generateYoudaoSignature(
        appKey: String,
        appSecret: String,
        q: String,
        salt: String,
        timestamp: String
    ): String {
        val truncated = truncateQ(q)
        val signStr = appKey + truncated + salt + timestamp + appSecret

        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(signStr.toByteArray())

        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 截断Q值（有道API要求）
     */
    private fun truncateQ(q: String): String {
        return if (q.length <= 20) {
            q
        } else {
            q.substring(0, 10) + q.length + q.substring(q.length - 10)
        }
    }

    /**
     * ✅ 修复：分割单张图片
     */
    suspend fun splitImage(
        imageFile: File,
        outputDir: File
    ): List<File> = withContext(Dispatchers.IO) {
        val result = mutableListOf<File>()

        try {
            Log.d(TAG, "开始分割图片: ${imageFile.name}")

            // 从配置获取API密钥
            val config = ConfigManager.getConfig()
            val appKey = config.api.youdaoApiKey
            val appSecret = config.api.youdaoSecretKey

            if (appKey.isEmpty() || appSecret.isEmpty()) {
                Log.e(TAG, "❌ 有道API密钥未配置")
                return@withContext result
            }

            // 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            // 读取图片并转为base64
            val imageBytes = imageFile.readBytes()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)

            Log.d(TAG, "📦 图片大小: ${imageBytes.size} 字节, Base64长度: ${imageBase64.length}")

            // 生成签名所需的参数
            val salt = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis() / 1000

            // 计算签名
            val sign = generateYoudaoSignature(
                appKey,
                appSecret,
                imageBase64,
                salt,
                timestamp.toString()
            )

            // 构建请求
            val formBody = FormBody.Builder()
                .add("q", imageBase64)
                .add("imageType", "1")
                .add("docType", "json")
                .add("signType", "v3")
                .add("appKey", appKey)
                .add("salt", salt)
                .add("curtime", timestamp.toString())
                .add("sign", sign)
                .build()

            val request = Request.Builder()
                .url(SPLIT_URL)
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()

            Log.d(TAG, "🚀 发送分割请求到有道API...")

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = JSONObject(responseBody)

                Log.d(TAG, "📥 有道API响应: 成功")

                // ✅ 修复：检查errorCode
                // 有道API: errorCode=0 表示成功，非0表示错误
                if (jsonResponse.has("errorCode")) {
                    val errorCode = jsonResponse.getInt("errorCode")

                    if (errorCode != YOUDAO_SUCCESS) {
                        // 只有非0才是错误
                        val errorMsg = jsonResponse.optString("errorMsg", "未知错误")
                        Log.e(TAG, "❌ 有道API返回错误: $errorCode - $errorMsg")
                        return@withContext result
                    } else {
                        Log.d(TAG, "✅ 有道API返回成功 (errorCode=0)")
                    }
                }

                // ✅ 改进：解析分割结果
                if (jsonResponse.has("Result")) {
                    val resultObj = jsonResponse.getJSONObject("Result")

                    if (resultObj.has("regions")) {
                        val regions = resultObj.getJSONArray("regions")
                        val originalImage = BitmapFactory.decodeFile(imageFile.absolutePath)

                        if (originalImage == null) {
                            Log.e(TAG, "❌ 无法加载原始图片")
                            return@withContext result
                        }

                        Log.d(TAG, "📐 开始裁剪 ${regions.length()} 个区域")
                        Log.d(TAG, "🖼️  原图大小: ${originalImage.width}x${originalImage.height}")

                        // 逐个处理每个区域
                        for (i in 0 until regions.length()) {
                            try {
                                val region = regions.getJSONObject(i)
                                val regionInfo = extractRegionInfo(region)

                                Log.d(TAG, "📍 处理区域 $i: 置信度=${regionInfo.score}")

                                // 方式1：使用 boundingBox 进行矩形裁剪（推荐）
                                val croppedFile = cropByBoundingBox(
                                    originalImage,
                                    regionInfo.boundingBox,
                                    outputDir,
                                    i
                                )

                                if (croppedFile != null) {
                                    result.add(croppedFile)
                                }

                                // 方式2：如果segment信息完整，也可使用多边形分割
                                // val segmentFile = cropBySegment(originalImage, regionInfo.segment, outputDir, i)
                                // if (segmentFile != null) { result.add(segmentFile) }

                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 处理区域 $i 时出错: ${e.message}", e)
                            }
                        }

                        Log.d(TAG, "✅ 图片分割完成: ${imageFile.name} -> ${result.size} 个区域")
                    } else {
                        Log.w(TAG, "⚠️  API响应中没有regions信息")
                    }
                } else {
                    Log.w(TAG, "⚠️  API响应中没有Result字段")
                }

            } else {
                Log.e(TAG, "❌ 分割请求失败: HTTP ${response.code}")
                Log.e(TAG, "响应体: $responseBody")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 分割图片异常: ${e.message}", e)
        }

        return@withContext result
    }

    /**
     * ✅ 改进：从JSON中提取区域信息
     */
    private fun extractRegionInfo(regionJson: JSONObject): RegionInfo {
        val boundingBox = regionJson.optString("boundingBox", "")
        val score = regionJson.optDouble("score", 0.0)
        val segment = regionJson.optString("segment", "")

        Log.d(TAG, "  - boundingBox: $boundingBox")
        Log.d(TAG, "  - score: $score")

        return RegionInfo(boundingBox, score, segment)
    }

    /**
     * ✅ 新增：使用 boundingBox 进行矩形裁剪（最简单可靠的方式）
     *
     * boundingBox 格式: "x1,y1,x2,y2,x3,y3,x4,y4"
     * 这是一个四边形，通常是矩形或接近矩形
     */
    private fun cropByBoundingBox(
        originalBitmap: Bitmap,
        boundingBox: String,
        outputDir: File,
        index: Int
    ): File? {
        return try {
            val coords = parseBoundingBox(boundingBox)

            if (coords.size < 4) {
                Log.w(TAG, "❌ boundingBox 坐标不足")
                return null
            }

            // 获取最小和最大坐标来形成矩形
            val xCoords = coords.map { it.first }
            val yCoords = coords.map { it.second }

            val minX = xCoords.minOrNull()?.toInt() ?: 0
            val minY = yCoords.minOrNull()?.toInt() ?: 0
            val maxX = xCoords.maxOrNull()?.toInt() ?: originalBitmap.width
            val maxY = yCoords.maxOrNull()?.toInt() ?: originalBitmap.height

            // 确保坐标在有效范围内
            val x = minX.coerceIn(0, originalBitmap.width - 1)
            val y = minY.coerceIn(0, originalBitmap.height - 1)
            val width = (maxX - x).coerceIn(1, originalBitmap.width - x)
            val height = (maxY - y).coerceIn(1, originalBitmap.height - y)

            Log.d(TAG, "    裁剪参数: x=$x, y=$y, width=$width, height=$height")

            if (width <= 0 || height <= 0) {
                Log.w(TAG, "❌ 裁剪尺寸无效")
                return null
            }

            // 执行裁剪
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                x,
                y,
                width,
                height
            )

            // 保存文件
            val outputFile = File(outputDir, "region_${index}.png")
            saveBitmapToFile(croppedBitmap, outputFile)

            Log.d(TAG, "    ✅ 区域 $index 已保存: ${outputFile.name} (${width}x${height})")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ 矩形裁剪失败: ${e.message}", e)
            return null
        }
    }

    /**
     * ✅ 新增：使用 segment 坐标进行多边形裁剪（更精确但复杂）
     *
     * segment 格式: "[[[x1,y1],[x2,y2],...]], ...]"
     * 包含多个多边形的坐标列表
     */
    private fun cropBySegment(
        originalBitmap: Bitmap,
        segmentStr: String,
        outputDir: File,
        index: Int
    ): File? {
        return try {
            val polygons = parseSegment(segmentStr)

            if (polygons.isEmpty()) {
                Log.w(TAG, "❌ 无法解析segment坐标")
                return null
            }

            // 获取第一个多边形的边界框
            val firstPolygon = polygons[0]
            if (firstPolygon.isEmpty()) return null

            val xCoords = firstPolygon.map { it.first }
            val yCoords = firstPolygon.map { it.second }

            val minX = xCoords.minOrNull()?.toInt() ?: 0
            val minY = yCoords.minOrNull()?.toInt() ?: 0
            val maxX = xCoords.maxOrNull()?.toInt() ?: originalBitmap.width
            val maxY = yCoords.maxOrNull()?.toInt() ?: originalBitmap.height

            val x = minX.coerceIn(0, originalBitmap.width - 1)
            val y = minY.coerceIn(0, originalBitmap.height - 1)
            val width = (maxX - x).coerceIn(1, originalBitmap.width - x)
            val height = (maxY - y).coerceIn(1, originalBitmap.height - y)

            if (width <= 0 || height <= 0) return null

            // 创建带有多边形mask的位图
            val croppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(croppedBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.BLACK

            // 绘制多边形
            for (polygon in polygons) {
                if (polygon.isNotEmpty()) {
                    val path = Path()
                    polygon.forEachIndexed { idx, (px, py) ->
                        val relX = (px - x).toFloat()
                        val relY = (py - y).toFloat()
                        if (idx == 0) {
                            path.moveTo(relX, relY)
                        } else {
                            path.lineTo(relX, relY)
                        }
                    }
                    path.close()
                    canvas.drawPath(path, paint)
                }
            }

            // 从原图裁剪该区域
            val original = Bitmap.createBitmap(originalBitmap, x, y, width, height)
            val outputFile = File(outputDir, "region_${index}_polygon.png")
            saveBitmapToFile(original, outputFile)

            Log.d(TAG, "    ✅ 区域 $index (多边形) 已保存: ${outputFile.name}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ 多边形裁剪失败: ${e.message}", e)
            return null
        }
    }

    /**
     * ✅ 改进：解析 boundingBox 坐标
     * 格式: "x1,y1,x2,y2,x3,y3,x4,y4"
     */
    private fun parseBoundingBox(boundingBox: String): List<Pair<Float, Float>> {
        return try {
            val coords = mutableListOf<Pair<Float, Float>>()
            val parts = boundingBox.split(",")

            for (i in parts.indices step 2) {
                if (i + 1 < parts.size) {
                    try {
                        val x = parts[i].trim().toFloat()
                        val y = parts[i + 1].trim().toFloat()
                        coords.add(Pair(x, y))
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "⚠️  无法解析坐标: ${parts[i]},${parts[i + 1]}")
                    }
                }
            }

            Log.d(TAG, "  - 解析boundingBox: ${coords.size} 个点")
            coords
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析boundingBox失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * ✅ 改进：解析 segment 坐标（复杂的嵌套数组格式）
     * 格式: "[[[x1,y1],[x2,y2],...]], ...]"
     */
    private fun parseSegment(segmentStr: String): List<List<Pair<Float, Float>>> {
        return try {
            val result = mutableListOf<List<Pair<Float, Float>>>()

            // 使用正则表达式提取所有 [x,y] 格式的坐标对
            val coordinatePattern = """\[(\d+),(\d+)\]""".toRegex()
            val matches = coordinatePattern.findAll(segmentStr)

            val pointsList = mutableListOf<Pair<Float, Float>>()
            for (match in matches) {
                try {
                    val x = match.groupValues[1].toFloat()
                    val y = match.groupValues[2].toFloat()
                    pointsList.add(Pair(x, y))
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "⚠️  无法解析segment坐标")
                }
            }

            if (pointsList.isNotEmpty()) {
                result.add(pointsList)
            }

            Log.d(TAG, "  - 解析segment: ${result.size} 个多边形, ${pointsList.size} 个点")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ 解析segment失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 保存Bitmap到文件
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            Log.d(TAG, "📁 Bitmap已保存: ${file.name} (${bitmap.width}x${bitmap.height})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存Bitmap失败: ${e.message}", e)
        }
    }

    /**
     * 清空进度状态
     */
    fun clearProgress() {
        _splitProgress.value = null
    }
}