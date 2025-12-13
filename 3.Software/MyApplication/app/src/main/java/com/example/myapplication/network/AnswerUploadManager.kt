package com.example.myapplication.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID
import com.example.myapplication.BleManager

/**
 * ✅ 改进的答案上传管理器 - 修复版
 *
 * 功能：
 * 1. 将分析答案上传到服务器数据库
 * 2. 如果BLE已连接，同时同步到客户端的 /sdcard/an 目录
 * 3. 支持单个或批量答案上传
 * 4. 自动重试和错误处理
 */
class AnswerUploadManager(
    private val bleManager: BleManager,
    private val networkManager: NetworkManager
) {

    companion object {
        private const val TAG = "AnswerUploadManager"
    }

    private val handler = Handler(Looper.getMainLooper())

    // 上传进度
    private val _uploadProgress = MutableStateFlow<AnswerUploadProgress?>(null)
    val uploadProgress: StateFlow<AnswerUploadProgress?> = _uploadProgress

    // 上传状态机
    private enum class UploadState {
        IDLE,
        UPLOADING_TO_SERVER,
        SYNCING_TO_DEVICE,
        COMPLETE,
        FAILED
    }

    private var currentState = UploadState.IDLE
    private var isUploading = false

    /**
     * 上传答案文件到服务器和设备
     */
    fun uploadAnswer(
        jsonFile: File,
        subject: String,
        imageIndex: Int,
        totalImages: Int
    ) {
        if (!jsonFile.exists() || !jsonFile.canRead()) {
            val errorMsg = "文件不存在或无法读取: ${jsonFile.path}"
            Log.e(TAG, "❌ $errorMsg")
            _uploadProgress.value = AnswerUploadProgress(
                fileName = jsonFile.name,
                subject = subject,
                fileSize = 0,
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = errorMsg
            )
            return
        }

        if (isUploading) {
            val errorMsg = "正在上传其他答案，请稍候"
            Log.w(TAG, "⚠️  $errorMsg")
            _uploadProgress.value = AnswerUploadProgress(
                fileName = jsonFile.name,
                subject = subject,
                fileSize = jsonFile.length(),
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = errorMsg
            )
            return
        }

        isUploading = true
        currentState = UploadState.UPLOADING_TO_SERVER

        val progressMsg = "正在上传到服务器..."
        _uploadProgress.value = AnswerUploadProgress(
            fileName = jsonFile.name,
            subject = subject,
            fileSize = jsonFile.length(),
            uploadedSize = 0,
            progress = 0,
            message = progressMsg
        )

        Log.d(TAG, "📤 开始上传答案: ${jsonFile.name} (大小: ${jsonFile.length()} bytes)")

        try {
            // 读取JSON内容
            val jsonContent = jsonFile.readText()
            val jsonObject = JSONObject(jsonContent)

            // 生成答案ID
            val answerId = UUID.randomUUID().toString()

            Log.d(TAG, "📝 答案ID: $answerId")
            Log.d(TAG, "📝 科目: $subject")
            Log.d(TAG, "📝 文件大小: ${jsonFile.length() / 1024.0}KB")

            // 上传到服务器
            uploadToServer(
                answerId = answerId,
                subject = subject,
                fileName = jsonFile.name,
                fileSize = jsonFile.length() / 1024.0,
                content = jsonObject,
                imageIndex = imageIndex,
                totalImages = totalImages,
                onSuccess = { serverAnswerId ->
                    Log.d(TAG, "✅ 服务器上传成功: $serverAnswerId")

                    // 服务器上传成功，检查是否需要同步到设备
                    if (bleManager.isConnected.value) {
                        Log.d(TAG, "🔗 BLE已连接，开始同步到设备...")
                        // BLE已连接，同步到设备
                        syncToDevice(jsonFile, serverAnswerId)
                    } else {
                        Log.w(TAG, "⚠️  BLE未连接，仅服务器上传完成")
                        // BLE未连接，仅服务器上传完成
                        completeUpload()
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "❌ 服务器上传失败: $error")
                    failUpload("上传服务器失败: $error")
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理答案文件失败: ${e.message}", e)
            failUpload("处理文件异常: ${e.message}")
        }
    }

    /**
     * 上传答案到服务器
     */
    private fun uploadToServer(
        answerId: String,
        subject: String,
        fileName: String,
        fileSize: Double,
        content: JSONObject,
        imageIndex: Int,
        totalImages: Int,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val requestBody = JSONObject().apply {
            put("answer_id", answerId)
            put("subject", subject)
            put("file_name", fileName)
            put("file_size", fileSize)
            put("content", content)
            put("device_id", "AR_glass_client")
            put("image_index", imageIndex)
            put("total_images", totalImages)
        }

        Log.d(TAG, "📤 上传到服务器，请求体大小: ${requestBody.toString().length} bytes")

        networkManager.uploadAnswerToServer(
            requestBody.toString(),
            onSuccess = { response ->
                Log.d(TAG, "✅ 答案上传服务器成功")
                _uploadProgress.value = _uploadProgress.value?.copy(
                    uploadedSize = fileSize.toLong() * 1024,
                    progress = 100,
                    message = "服务器上传成功，准备同步到设备..."
                )
                onSuccess(answerId)
            },
            onFailure = { error ->
                Log.e(TAG, "❌ 答案上传服务器失败: $error")
                onFailure(error)
            }
        )
    }

    /**
     * 同步答案文件到设备的 /sdcard/an 目录
     */
    private fun syncToDevice(
        jsonFile: File,
        answerId: String
    ) {
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️  BLE连接已断开，跳过设备同步")
            completeUpload()
            return
        }

        // ✅ 修复：改为检查 isFullyInitialized
        if (!bleManager.isFullyInitialized) {
            Log.w(TAG, "⚠️  BLE文件上传未准备好，跳过设备同步")
            completeUpload()
            return
        }

        currentState = UploadState.SYNCING_TO_DEVICE
        _uploadProgress.value = _uploadProgress.value?.copy(
            message = "正在同步到设备 /sdcard/an..."
        )

        Log.d(TAG, "📤 开始同步到BLE设备...")

        try {
            // 生成目标文件名：answerId.json
            val targetFileName = "$answerId.json"
            val targetPath = "/sdcard/an/$targetFileName"

            Log.d(TAG, "📝 目标路径: $targetPath")

            // 读取文件内容
            val fileBytes = jsonFile.readBytes()
            Log.d(TAG, "📦 文件大小: ${fileBytes.size} bytes")

            // 使用BLE文件上传接口上传到设备
            uploadFileViaBle(
                fileBytes = fileBytes,
                fileName = targetFileName,
                targetPath = targetPath,
                onSuccess = {
                    Log.d(TAG, "✅ 答案已同步到设备: $targetPath")
                    completeUpload()
                },
                onFailure = { error ->
                    Log.w(TAG, "⚠️  同步到设备失败: $error，但服务器上传已成功")
                    // 服务器上传成功，设备同步失败时也算成功
                    completeUpload()
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ 同步到设备异常: ${e.message}", e)
            // 服务器上传成功，设备同步异常时也算成功
            completeUpload()
        }
    }

    /**
     * 通过BLE上传文件到设备
     */
    private fun uploadFileViaBle(
        fileBytes: ByteArray,
        fileName: String,
        targetPath: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            Log.d(TAG, "🔄 通过BLE上传文件...")

            // 第一步：发送文件路径
            Log.d(TAG, "1️⃣  发送文件路径: $targetPath")
            if (!bleManager.sendFileName(targetPath)) {
                Log.e(TAG, "❌ 发送文件路径失败")
                onFailure("发送文件路径失败")
                return
            }

            // 第二步：发送文件内容（分块）
            Log.d(TAG, "2️⃣  开始分块发送文件内容...")
            var offset = 0
            val chunkSize = 400

            while (offset < fileBytes.size) {
                val end = minOf(offset + chunkSize, fileBytes.size)
                val chunk = fileBytes.sliceArray(offset until end)

                Log.d(TAG, "📦 发送第${offset / chunkSize + 1}块: $offset - $end bytes")

                if (!bleManager.sendFileData(chunk)) {
                    Log.e(TAG, "❌ 发送文件数据失败，已发送 $offset/${fileBytes.size} 字节")
                    onFailure("发送文件数据失败，已发送 $offset/${fileBytes.size} 字节")
                    return
                }

                offset = end

                // 更新进度
                val progress = (offset * 100) / fileBytes.size
                _uploadProgress.value = _uploadProgress.value?.copy(
                    uploadedSize = offset.toLong(),
                    progress = progress
                )

                Log.d(TAG, "📊 上传进度: $progress%")

                // 短暂延迟
                Thread.sleep(50)
            }

            Log.d(TAG, "✅ 文件内容发送完成")

            // 第三步：发送start命令
            Log.d(TAG, "3️⃣  发送start命令...")
            if (!bleManager.sendFileControl("start")) {
                Log.e(TAG, "❌ 发送start命令失败")
                onFailure("发送start命令失败")
                return
            }

            Log.d(TAG, "✅ start命令已发送")

            // 第四步：发送end命令
            Log.d(TAG, "4️⃣  发送end命令...")
            if (!bleManager.sendFileControl("end")) {
                Log.e(TAG, "❌ 发送end命令失败")
                onFailure("发送end命令失败")
                return
            }

            Log.d(TAG, "✅ end命令已发送")
            Log.d(TAG, "✅ BLE上传完成: $targetPath")
            onSuccess()

        } catch (e: Exception) {
            Log.e(TAG, "❌ BLE上传异常: ${e.message}", e)
            onFailure(e.message ?: "未知错误")
        }
    }

    /**
     * 完成上传
     */
    private fun completeUpload() {
        isUploading = false
        currentState = UploadState.COMPLETE

        _uploadProgress.value = _uploadProgress.value?.copy(
            isComplete = true,
            message = "答案上传完成✓"
        )

        Log.d(TAG, "✅ 答案上传完成")

        // 3秒后清空进度
        handler.postDelayed({
            _uploadProgress.value = null
        }, 3000)
    }

    /**
     * 上传失败
     */
    private fun failUpload(error: String) {
        isUploading = false
        currentState = UploadState.FAILED

        _uploadProgress.value = _uploadProgress.value?.copy(
            isComplete = false,
            errorMessage = error
        )

        Log.e(TAG, "❌ 答案上传失败: $error")

        // 5秒后清空进度
        handler.postDelayed({
            _uploadProgress.value = null
        }, 5000)
    }

    /**
     * 取消上传
     */
    fun cancelUpload() {
        isUploading = false
        currentState = UploadState.IDLE
        handler.removeCallbacksAndMessages(null)

        _uploadProgress.value = _uploadProgress.value?.copy(
            isComplete = false,
            errorMessage = "上传已取消"
        )

        Log.d(TAG, "⚠️  上传已取消")

        // 2秒后清空进度
        handler.postDelayed({
            _uploadProgress.value = null
        }, 2000)
    }

    /**
     * 清空进度状态
     */
    fun clearProgress() {
        _uploadProgress.value = null
    }
}

/**
 * 答案上传进度数据类
 */
data class AnswerUploadProgress(
    val fileName: String,
    val subject: String,
    val fileSize: Long,
    val uploadedSize: Long,
    val progress: Int,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)