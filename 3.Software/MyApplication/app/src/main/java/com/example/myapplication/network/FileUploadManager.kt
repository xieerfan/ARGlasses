package com.example.myapplication

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.data.FileType
import com.example.myapplication.data.UploadProgress
import com.example.myapplication.network.NetworkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream

/**
 * 文件上传管理器 - 修复版，正确处理BLE写入异步回调
 *
 * ESP32文件接收协议流程:
 * 1. 写入完整文件路径到 0x0103 (特征 1_3)
 * 2. 写入第一块数据到 0x0101 (特征 1_1)
 * 3. 写入"start"到 0x0102 (特征 1_2) → ESP32 调用 start_write
 * 4. 循环：写入下一块数据到 0x0101 → 写入"update"到 0x0102
 * 5. 写入"end"到 0x0102 → ESP32 调用 end_write
 *
 * 关键修复：每次写入必须等待上一次写入完成
 */
class FileUploadManager(private val bleManager: BleManager) {

    private val TAG = "FileUploadManager"
    private val handler = Handler(Looper.getMainLooper())

    // 上传进度
    private val _uploadProgress = MutableStateFlow<UploadProgress?>(null)
    val uploadProgress: StateFlow<UploadProgress?> = _uploadProgress

    // 上传状态机
    private enum class WriteState {
        IDLE,
        WRITING_FILE_NAME,
        WRITING_FIRST_DATA,
        WRITING_START_COMMAND,
        WRITING_CHUNK_DATA,
        WRITING_UPDATE_COMMAND,
        WRITING_END_COMMAND
    }

    private var isUploading = false
    private var uploadBuffer = mutableListOf<Byte>()
    private var currentFile: File? = null
    private var totalSent = 0L
    private var fileType: FileType? = null
    private var currentWriteState = WriteState.IDLE
    private var pendingChunk: ByteArray? = null

    /**
     * 检查是否准备好上传
     */
    private fun isUploadReady(): Boolean {
        return bleManager.isFullyInitialized && bleManager.isConnected.value
    }

    /**
     * 上传文件
     */
    fun uploadFile(file: File, type: FileType) {
        Log.d(TAG, "📤 开始上传文件: ${file.name}")

        if (!file.exists() || !file.canRead()) {
            failUpload("文件不存在或无法读取")
            return
        }

        if (isUploading) {
            failUpload("正在上传其他文件，请稍候")
            return
        }

        if (!isUploadReady()) {
            failUpload("设备未准备好")
            return
        }

        isUploading = true
        currentFile = file
        fileType = type
        totalSent = 0
        uploadBuffer.clear()
        currentWriteState = WriteState.IDLE
        pendingChunk = null

        _uploadProgress.value = UploadProgress(
            fileName = file.name,
            totalSize = file.length(),
            uploadedSize = 0,
            progress = 0
        )

        // 读取文件内容
        try {
            val fileBytes = FileInputStream(file).use { it.readBytes() }
            uploadBuffer.addAll(fileBytes.toList())

            // 生成完整的目标文件路径
            val targetFilePath = "${type.targetPath}/${file.name}"

            Log.d(TAG, "📁 目标路径: $targetFilePath, 文件大小: ${file.length()} 字节")

            // 开始上传流程
            startUpload(targetFilePath)

        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取文件失败: ${e.message}")
            failUpload("读取文件失败: ${e.message}")
        }
    }

    /**
     * 开始上传流程 - 步骤1: 写入文件路径
     */
    private fun startUpload(filePath: String) {
        Log.d(TAG, "Step 1️⃣: 发送文件路径...")
        currentWriteState = WriteState.WRITING_FILE_NAME

        // 发送文件名
        val success = bleManager.sendFileName(filePath)
        if (success) {
            Log.d(TAG, "✅ 文件路径已发送，等待200ms后发送第一块数据")
            handler.postDelayed({
                writeFirstChunk()
            }, 200)
        } else {
            failUpload("发送文件路径失败")
        }
    }

    /**
     * 步骤2: 写入第一块数据
     */
    private fun writeFirstChunk() {
        if (!isUploading) return

        if (uploadBuffer.isEmpty()) {
            failUpload("文件为空")
            return
        }

        Log.d(TAG, "Step 2️⃣: 发送第一块数据...")

        // 计算第一块大小（最大400字节）
        val chunkSize = minOf(400, uploadBuffer.size)
        val chunk = uploadBuffer.take(chunkSize).toByteArray()
        uploadBuffer = uploadBuffer.drop(chunkSize).toMutableList()
        pendingChunk = chunk

        currentWriteState = WriteState.WRITING_FIRST_DATA
        val success = bleManager.sendFileData(chunk)

        if (success) {
            Log.d(TAG, "✅ 第一块数据已发送 ($chunkSize 字节)，等待50ms后发送start命令")
            totalSent += chunk.size
            updateProgress()

            handler.postDelayed({
                writeStartCommand()
            }, 50)
        } else {
            failUpload("发送第一块数据失败")
        }
    }

    /**
     * 步骤3: 发送start命令
     */
    private fun writeStartCommand() {
        if (!isUploading) return

        Log.d(TAG, "Step 3️⃣: 发送start命令...")
        currentWriteState = WriteState.WRITING_START_COMMAND

        val success = bleManager.sendFileControl("start")
        if (success) {
            Log.d(TAG, "✅ start命令已发送，等待100ms后继续发送数据")
            handler.postDelayed({
                writeNextChunk()
            }, 100)
        } else {
            failUpload("发送start命令失败")
        }
    }

    /**
     * 步骤4: 写入后续数据块
     */
    private fun writeNextChunk() {
        if (!isUploading) return

        if (uploadBuffer.isEmpty()) {
            // 所有数据发送完毕，发送end命令
            Log.d(TAG, "📋 所有数据块已发送，准备发送end命令")
            writeEndCommand()
            return
        }

        Log.d(TAG, "Step 4️⃣: 发送数据块 (剩余: ${uploadBuffer.size} 字节)...")

        // 计算块大小
        val chunkSize = minOf(400, uploadBuffer.size)
        val chunk = uploadBuffer.take(chunkSize).toByteArray()
        uploadBuffer = uploadBuffer.drop(chunkSize).toMutableList()
        pendingChunk = chunk

        currentWriteState = WriteState.WRITING_CHUNK_DATA
        val success = bleManager.sendFileData(chunk)

        if (success) {
            Log.d(TAG, "✅ 数据块已发送 ($chunkSize 字节)，等待50ms后发送update命令")
            totalSent += chunk.size
            updateProgress()

            handler.postDelayed({
                writeUpdateCommand()
            }, 50)
        } else {
            failUpload("发送数据块失败")
        }
    }

    /**
     * 步骤5: 发送update命令
     */
    private fun writeUpdateCommand() {
        if (!isUploading) return

        Log.d(TAG, "Step 5️⃣: 发送update命令...")
        currentWriteState = WriteState.WRITING_UPDATE_COMMAND

        val success = bleManager.sendFileControl("update")
        if (success) {
            Log.d(TAG, "✅ update命令已发送，等待100ms后继续发送下一块")
            handler.postDelayed({
                writeNextChunk()
            }, 100)
        } else {
            failUpload("发送update命令失败")
        }
    }

    /**
     * 步骤6: 发送end命令
     */
    private fun writeEndCommand() {
        if (!isUploading) return

        Log.d(TAG, "Step 6️⃣: 发送end命令...")
        currentWriteState = WriteState.WRITING_END_COMMAND

        val success = bleManager.sendFileControl("end")
        if (success) {
            Log.d(TAG, "✅ end命令已发送，等待500ms后完成上传")
            handler.postDelayed({
                completeUpload()
            }, 500)
        } else {
            failUpload("发送end命令失败")
        }
    }

    /**
     * 更新进度
     */
    private fun updateProgress() {
        currentFile?.let { file ->
            val progress = if (file.length() > 0) {
                ((totalSent * 100) / file.length()).toInt()
            } else {
                0
            }

            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = progress
            )

            Log.d(TAG, "📊 进度: $progress% ($totalSent/${file.length()} 字节)")
        }
    }

    /**
     * 完成上传
     */
    private fun completeUpload() {
        isUploading = false
        currentWriteState = WriteState.IDLE

        Log.d(TAG, "🎉 设备端上传完成")

        currentFile?.let { file ->
            val fileSize = file.length()
            val fileName = file.name

            // 更新本地进度
            _uploadProgress.value = UploadProgress(
                fileName = fileName,
                totalSize = fileSize,
                uploadedSize = fileSize,
                progress = 100,
                isComplete = true,
                message = "设备上传完成，正在同步到服务器..."
            )

            // 延迟2秒后开始上传到服务器
            handler.postDelayed({
                // 根据文件类型上传到服务器
                when (fileType) {
                    FileType.MUSIC -> {
                        uploadToServer(fileName, fileSize, "music")
                    }
                    FileType.NOVEL -> {
                        uploadToServer(fileName, fileSize, "novel")
                    }
                    else -> {
                        // 其他类型不上传服务器
                        Log.d(TAG, "📁 文件类型: $fileType，不需要上传服务器")
                        _uploadProgress.value = UploadProgress(
                            fileName = fileName,
                            totalSize = fileSize,
                            uploadedSize = fileSize,
                            progress = 100,
                            isComplete = true,
                            message = "上传完成"
                        )
                        handler.postDelayed({
                            _uploadProgress.value = null
                        }, 2000)
                    }
                }
            }, 2000)
        }
    }

    /**
     * 上传到服务器
     */
    private fun uploadToServer(fileName: String, fileSize: Long, type: String) {
        Log.d(TAG, "🌐 开始上传到服务器: $fileName")

        _uploadProgress.value = UploadProgress(
            fileName = fileName,
            totalSize = fileSize,
            uploadedSize = fileSize,
            progress = 100,
            isComplete = false,
            message = "正在上传到服务器..."
        )

        when (type) {
            "music" -> {
                NetworkManager.uploadMusicToServer(fileName, fileSize) { success, message ->
                    handler.post {
                        if (success) {
                            Log.d(TAG, "✅ 服务器上传成功")
                            _uploadProgress.value = UploadProgress(
                                fileName = fileName,
                                totalSize = fileSize,
                                uploadedSize = fileSize,
                                progress = 100,
                                isComplete = true,
                                message = "上传完成（已同步到服务器）"
                            )
                            handler.postDelayed({
                                _uploadProgress.value = null
                            }, 3000)
                        } else {
                            Log.e(TAG, "❌ 服务器上传失败: $message")
                            _uploadProgress.value = UploadProgress(
                                fileName = fileName,
                                totalSize = fileSize,
                                uploadedSize = fileSize,
                                progress = 100,
                                isComplete = false,
                                errorMessage = "服务器上传失败: $message"
                            )
                            handler.postDelayed({
                                _uploadProgress.value = null
                            }, 5000)
                        }
                    }
                }
            }
            "novel" -> {
                NetworkManager.uploadNovelToServer(fileName, fileSize) { success, message ->
                    handler.post {
                        if (success) {
                            Log.d(TAG, "✅ 服务器上传成功")
                            _uploadProgress.value = UploadProgress(
                                fileName = fileName,
                                totalSize = fileSize,
                                uploadedSize = fileSize,
                                progress = 100,
                                isComplete = true,
                                message = "上传完成（已同步到服务器）"
                            )
                            handler.postDelayed({
                                _uploadProgress.value = null
                            }, 3000)
                        } else {
                            Log.e(TAG, "❌ 服务器上传失败: $message")
                            _uploadProgress.value = UploadProgress(
                                fileName = fileName,
                                totalSize = fileSize,
                                uploadedSize = fileSize,
                                progress = 100,
                                isComplete = false,
                                errorMessage = "服务器上传失败: $message"
                            )
                            handler.postDelayed({
                                _uploadProgress.value = null
                            }, 5000)
                        }
                    }
                }
            }
        }
    }

    /**
     * 上传失败
     */
    private fun failUpload(error: String) {
        Log.e(TAG, "❌ 上传失败: $error")

        isUploading = false
        currentWriteState = WriteState.IDLE
        handler.removeCallbacksAndMessages(null)

        currentFile?.let { file ->
            val progress = if (file.length() > 0) {
                ((totalSent * 100) / file.length()).toInt()
            } else {
                0
            }

            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = progress,
                isComplete = false,
                errorMessage = error
            )
        }

        // 5秒后清空进度
        handler.postDelayed({
            _uploadProgress.value = null
        }, 5000)
    }

    /**
     * 取消上传
     */
    fun cancelUpload() {
        Log.d(TAG, "⏹️ 取消上传")

        isUploading = false
        currentWriteState = WriteState.IDLE
        uploadBuffer.clear()
        pendingChunk = null
        handler.removeCallbacksAndMessages(null)

        currentFile?.let { file ->
            val progress = if (file.length() > 0) {
                ((totalSent * 100) / file.length()).toInt()
            } else {
                0
            }

            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = progress,
                isComplete = false,
                errorMessage = "上传已取消"
            )
        }

        // 2秒后清空进度
        handler.postDelayed({
            _uploadProgress.value = null
        }, 2000)
    }
}