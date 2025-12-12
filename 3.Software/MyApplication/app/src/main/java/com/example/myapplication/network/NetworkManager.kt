// 位置: com/example/myapplication/network/NetworkManager.kt
package com.example.myapplication.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

// 网络请求结果
sealed class NetworkResult<T> {
    data class Success<T>(val data: T, val message: String = "") : NetworkResult<T>()
    data class Error<T>(val message: String, val code: Int = -1) : NetworkResult<T>()
    data class Loading<T>(val isLoading: Boolean = true) : NetworkResult<T>()
}

// 上传到服务器的响应
data class ServerUploadResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any>? = null
)

// 网络管理器单例
object NetworkManager {
    private const val TAG = "NetworkManager"

    // 网络请求状态
    private val _uploadState = MutableStateFlow<NetworkResult<ServerUploadResponse>?>(null)
    val uploadState: StateFlow<NetworkResult<ServerUploadResponse>?> = _uploadState

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // ==================== ✅ 新增：答案上传接口 ====================

    /**
     * 上传分析答案到服务器
     */
    fun uploadAnswerToServer(
        requestBody: String,
        onSuccess: (ServerUploadResponse) -> Unit,
        onFailure: (String) -> Unit
    ) {
        _uploadState.value = NetworkResult.Loading()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = com.example.myapplication.config.ConfigManager.getConfig()
                val serverIp = config.server.ip
                val serverPort = config.server.port

                if (serverIp.isEmpty() || serverPort.isEmpty()) {
                    _uploadState.value = NetworkResult.Error("服务器配置未设置")
                    onFailure("服务器配置未设置")
                    return@launch
                }

                val url = "http://${serverIp}:${serverPort}/api/upload/answer"

                val body = requestBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                Log.d(TAG, "上传答案到服务器, URL: $url")

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    val message = jsonResponse.optString("message", "")

                    if (success) {
                        val data = jsonResponse.optJSONObject("data")
                        val dataMap = mutableMapOf<String, Any>()

                        if (data != null) {
                            val keys = data.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                dataMap[key] = data.get(key)
                            }
                        }

                        val serverResponse = ServerUploadResponse(
                            success = true,
                            message = message,
                            data = dataMap
                        )

                        _uploadState.value = NetworkResult.Success(serverResponse)
                        Log.d(TAG, "答案上传到服务器成功: $message")
                        onSuccess(serverResponse)
                    } else {
                        _uploadState.value = NetworkResult.Error(message)
                        Log.e(TAG, "答案上传到服务器失败: $message")
                        onFailure(message)
                    }
                } else {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    _uploadState.value = NetworkResult.Error(errorMsg)
                    Log.e(TAG, "答案上传到服务器HTTP错误: $errorMsg")
                    onFailure(errorMsg)
                }

            } catch (e: SocketTimeoutException) {
                val errorMsg = "连接超时，请检查服务器配置"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "答案上传到服务器超时: ${e.message}")
                onFailure(errorMsg)
            } catch (e: IOException) {
                val errorMsg = "网络连接错误: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "答案上传到服务器IO错误: ${e.message}")
                onFailure(errorMsg)
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "答案上传到服务器异常: ${e.message}")
                onFailure(errorMsg)
            }
        }
    }

    // ==================== 原有接口：小说和音乐 ====================

    // 上传小说信息到服务器
    fun uploadNovelToServer(novelName: String, fileSize: Long, onComplete: (Boolean, String) -> Unit) {
        _uploadState.value = NetworkResult.Loading()

        val config = com.example.myapplication.config.ConfigManager.getConfig()
        val serverIp = config.server.ip
        val serverPort = config.server.port

        if (serverIp.isEmpty() || serverPort.isEmpty()) {
            _uploadState.value = NetworkResult.Error("服务器配置未设置")
            onComplete(false, "服务器配置未设置")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "http://${serverIp}:${serverPort}/api/upload/novel"

                val json = JSONObject().apply {
                    put("novel_name", novelName)
                    put("file_size", fileSize / (1024.0 * 1024.0))
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                Log.d(TAG, "上传小说到服务器: $novelName, 大小: ${fileSize / (1024.0 * 1024.0)}MB, URL: $url")

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    val message = jsonResponse.optString("message", "")

                    if (success) {
                        val data = jsonResponse.optJSONObject("data")
                        val dataMap = mutableMapOf<String, Any>()

                        if (data != null) {
                            val keys = data.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                dataMap[key] = data.get(key)
                            }
                        }

                        val serverResponse = ServerUploadResponse(
                            success = true,
                            message = message,
                            data = dataMap
                        )

                        _uploadState.value = NetworkResult.Success(serverResponse)
                        Log.d(TAG, "小说上传到服务器成功: $message")
                        onComplete(true, message)
                    } else {
                        _uploadState.value = NetworkResult.Error(message)
                        Log.e(TAG, "小说上传到服务器失败: $message")
                        onComplete(false, message)
                    }
                } else {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    _uploadState.value = NetworkResult.Error(errorMsg)
                    Log.e(TAG, "小说上传到服务器HTTP错误: $errorMsg")
                    onComplete(false, errorMsg)
                }

            } catch (e: SocketTimeoutException) {
                val errorMsg = "连接超时，请检查服务器配置"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "小说上传到服务器超时: ${e.message}")
                onComplete(false, errorMsg)
            } catch (e: IOException) {
                val errorMsg = "网络连接错误: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "小说上传到服务器IO错误: ${e.message}")
                onComplete(false, errorMsg)
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "小说上传到服务器异常: ${e.message}")
                onComplete(false, errorMsg)
            }
        }
    }

    // 上传音乐信息到服务器
    fun uploadMusicToServer(musicName: String, fileSize: Long, onComplete: (Boolean, String) -> Unit) {
        _uploadState.value = NetworkResult.Loading()

        val config = com.example.myapplication.config.ConfigManager.getConfig()
        val serverIp = config.server.ip
        val serverPort = config.server.port

        if (serverIp.isEmpty() || serverPort.isEmpty()) {
            _uploadState.value = NetworkResult.Error("服务器配置未设置")
            onComplete(false, "服务器配置未设置")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "http://${serverIp}:${serverPort}/api/upload/music"

                val json = JSONObject().apply {
                    put("music_name", musicName)
                    put("file_size", fileSize / (1024.0 * 1024.0))
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()

                Log.d(TAG, "上传音乐到服务器: $musicName, 大小: ${fileSize / (1024.0 * 1024.0)}MB, URL: $url")

                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    val message = jsonResponse.optString("message", "")

                    if (success) {
                        val data = jsonResponse.optJSONObject("data")
                        val dataMap = mutableMapOf<String, Any>()

                        if (data != null) {
                            val keys = data.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                dataMap[key] = data.get(key)
                            }
                        }

                        val serverResponse = ServerUploadResponse(
                            success = true,
                            message = message,
                            data = dataMap
                        )

                        _uploadState.value = NetworkResult.Success(serverResponse)
                        Log.d(TAG, "音乐上传到服务器成功: $message")
                        onComplete(true, message)
                    } else {
                        _uploadState.value = NetworkResult.Error(message)
                        Log.e(TAG, "音乐上传到服务器失败: $message")
                        onComplete(false, message)
                    }
                } else {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    _uploadState.value = NetworkResult.Error(errorMsg)
                    Log.e(TAG, "音乐上传到服务器HTTP错误: $errorMsg")
                    onComplete(false, errorMsg)
                }

            } catch (e: SocketTimeoutException) {
                val errorMsg = "连接超时，请检查服务器配置"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "音乐上传到服务器超时: ${e.message}")
                onComplete(false, errorMsg)
            } catch (e: IOException) {
                val errorMsg = "网络连接错误: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "音乐上传到服务器IO错误: ${e.message}")
                onComplete(false, errorMsg)
            } catch (e: Exception) {
                val errorMsg = "上传失败: ${e.message}"
                _uploadState.value = NetworkResult.Error(errorMsg)
                Log.e(TAG, "音乐上传到服务器异常: ${e.message}")
                onComplete(false, errorMsg)
            }
        }
    }

    // 清空状态
    fun clearState() {
        _uploadState.value = null
    }
}
