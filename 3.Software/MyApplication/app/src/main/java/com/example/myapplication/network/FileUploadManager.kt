// FileUploadManager.kt（修正版）
package com.example.myapplication

import android.os.Handler
import android.os.Looper
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
 * 1. 写入完整文件路径到 0x0103
 * 2. 写入第一块数据到 0x0101
 * 3. 写入"start"到 0x0102 → ESP32调用start_write打开文件并写入0x0101中的数据
 * 4. 循环：写入下一块数据到 0x0101 → 写入"update"到 0x0102
 * 5. 写入"end"到 0x0102 → ESP32调用end_write关闭文件
 *
 * 关键修复：每次写入必须等待上一次写入的回调完成
 */
class FileUploadManager(private val bleManager: BleManager) {

    private val handler = Handler(Looper.getMainLooper())

    // 上传进度
    private val _uploadProgress = MutableStateFlow<UploadProgress?>(null)
    val uploadProgress: StateFlow<UploadProgress?> = _uploadProgress

    // 上传状态
    private var isUploading = false
    private var uploadBuffer = mutableListOf<Byte>()
    private var currentFile: File? = null
    private var totalSent = 0L
    private var fileType: FileType? = null

    // 写入状态机
    private enum class WriteState {
        IDLE,
        WRITING_FILE_NAME,
        WRITING_FIRST_DATA,
        WRITING_START_COMMAND,
        WRITING_CHUNK_DATA,
        WRITING_UPDATE_COMMAND,
        WRITING_END_COMMAND
    }

    private var currentWriteState = WriteState.IDLE
    private var pendingChunk: ByteArray? = null

    /**
     * 上传文件
     */
    fun uploadFile(file: File, type: FileType) {
        if (!file.exists() || !file.canRead()) {
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = 0,
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = "文件不存在或无法读取"
            )
            return
        }

        if (isUploading) {
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = 0,
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = "正在上传其他文件，请稍候"
            )
            return
        }

        if (!bleManager.isFileUploadReady()) {
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = 0,
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = "设备未准备好"
            )
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

            // 注册写入回调
            bleManager.setWriteCallback(writeCallback)

            // 开始上传流程
            startUpload(targetFilePath)

        } catch (e: Exception) {
            isUploading = false
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = 0,
                progress = 0,
                isComplete = false,
                errorMessage = "读取文件失败: ${e.message}"
            )
        }
    }

    /**
     * BLE写入回调 - 所有写入操作必须等待此回调
     */
    private val writeCallback = object : BleManager.WriteCallback {
        override fun onWriteSuccess() {
            when (currentWriteState) {
                WriteState.WRITING_FILE_NAME -> {
                    // 文件名写入成功，继续写入第一块数据
                    handler.postDelayed({
                        writeFirstChunk()
                    }, 50)
                }
                WriteState.WRITING_FIRST_DATA -> {
                    // 第一块数据写入成功，发送start命令
                    handler.postDelayed({
                        writeStartCommand()
                    }, 50)
                }
                WriteState.WRITING_START_COMMAND -> {
                    // start命令发送成功，继续发送下一块
                    handler.postDelayed({
                        writeNextChunk()
                    }, 100)
                }
                WriteState.WRITING_CHUNK_DATA -> {
                    // 数据块写入成功，发送update命令
                    handler.postDelayed({
                        writeUpdateCommand()
                    }, 50)
                }
                WriteState.WRITING_UPDATE_COMMAND -> {
                    // update命令发送成功，继续发送下一块
                    handler.postDelayed({
                        writeNextChunk()
                    }, 100)
                }
                WriteState.WRITING_END_COMMAND -> {
                    // end命令发送成功，上传完成
                    completeUpload()
                }
                else -> {}
            }
        }

        override fun onWriteFailure(error: String) {
            failUpload("写入失败 (${currentWriteState.name}): $error")
        }
    }

    /**
     * 开始上传流程 - 步骤1: 写入文件路径
     */
    private fun startUpload(filePath: String) {
        currentWriteState = WriteState.WRITING_FILE_NAME
        if (!bleManager.sendFileName(filePath)) {
            failUpload("发送文件路径失败")
        }
    }

    /**
     * 步骤2: 写入第一块数据
     */
    private fun writeFirstChunk() {
        if (uploadBuffer.isEmpty()) {
            failUpload("文件为空")
            return
        }

        // 计算第一块大小（最大400字节）
        val chunkSize = minOf(400, uploadBuffer.size)
        val chunk = uploadBuffer.take(chunkSize).toByteArray()
        uploadBuffer = uploadBuffer.drop(chunkSize).toMutableList()
        pendingChunk = chunk

        currentWriteState = WriteState.WRITING_FIRST_DATA
        if (!bleManager.sendFileData(chunk)) {
            failUpload("发送第一块数据失败")
            return
        }

        totalSent += chunk.size
        updateProgress()
    }

    /**
     * 步骤3: 发送start命令
     */
    private fun writeStartCommand() {
        currentWriteState = WriteState.WRITING_START_COMMAND
        if (!bleManager.sendFileControl("start")) {
            failUpload("发送start命令失败")
        }
    }

    /**
     * 步骤4: 写入后续数据块
     */
    private fun writeNextChunk() {
        if (!isUploading) {
            return
        }

        if (uploadBuffer.isEmpty()) {
            // 所有数据发送完毕，发送end命令
            writeEndCommand()
            return
        }

        // 计算块大小
        val chunkSize = minOf(400, uploadBuffer.size)
        val chunk = uploadBuffer.take(chunkSize).toByteArray()
        uploadBuffer = uploadBuffer.drop(chunkSize).toMutableList()
        pendingChunk = chunk

        currentWriteState = WriteState.WRITING_CHUNK_DATA
        if (!bleManager.sendFileData(chunk)) {
            failUpload("发送数据块失败")
            return
        }

        totalSent += chunk.size
        updateProgress()
    }

    /**
     * 步骤5: 发送update命令
     */
    private fun writeUpdateCommand() {
        currentWriteState = WriteState.WRITING_UPDATE_COMMAND
        if (!bleManager.sendFileControl("update")) {
            failUpload("发送update命令失败")
        }
    }

    /**
     * 步骤6: 发送end命令
     */
    private fun writeEndCommand() {
        currentWriteState = WriteState.WRITING_END_COMMAND
        if (!bleManager.sendFileControl("end")) {
            failUpload("发送end命令失败")
        }
    }

    /**
     * 更新进度
     */
    private fun updateProgress() {
        currentFile?.let { file ->
            val progress = if (file.length() > 0) ((totalSent * 100) / file.length()).toInt() else 0
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = progress
            )
        }
    }

    /**
     * 完成上传 - 修改版，增加服务器上传
     */
    private fun completeUpload() {
        isUploading = false
        currentWriteState = WriteState.IDLE
        bleManager.setWriteCallback(null)

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
        currentFile?.let { file ->
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
                                _uploadProgress.value = UploadProgress(
                                    fileName = fileName,
                                    totalSize = fileSize,
                                    uploadedSize = fileSize,
                                    progress = 100,
                                    isComplete = true,
                                    message = "上传完成（已同步到服务器）"
                                )

                                // 3秒后清空进度
                                handler.postDelayed({
                                    _uploadProgress.value = null
                                }, 3000)
                            } else {
                                _uploadProgress.value = UploadProgress(
                                    fileName = fileName,
                                    totalSize = fileSize,
                                    uploadedSize = fileSize,
                                    progress = 100,
                                    isComplete = false,
                                    errorMessage = "设备上传成功，但服务器上传失败: $message"
                                )

                                // 5秒后清空进度
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
                                _uploadProgress.value = UploadProgress(
                                    fileName = fileName,
                                    totalSize = fileSize,
                                    uploadedSize = fileSize,
                                    progress = 100,
                                    isComplete = true,
                                    message = "上传完成（已同步到服务器）"
                                )

                                // 3秒后清空进度
                                handler.postDelayed({
                                    _uploadProgress.value = null
                                }, 3000)
                            } else {
                                _uploadProgress.value = UploadProgress(
                                    fileName = fileName,
                                    totalSize = fileSize,
                                    uploadedSize = fileSize,
                                    progress = 100,
                                    isComplete = false,
                                    errorMessage = "设备上传成功，但服务器上传失败: $message"
                                )

                                // 5秒后清空进度
                                handler.postDelayed({
                                    _uploadProgress.value = null
                                }, 5000)
                            }
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
        isUploading = false
        currentWriteState = WriteState.IDLE
        bleManager.setWriteCallback(null)

        currentFile?.let { file ->
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = if (file.length() > 0) ((totalSent * 100) / file.length()).toInt() else 0,
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
        isUploading = false
        currentWriteState = WriteState.IDLE
        uploadBuffer.clear()
        pendingChunk = null
        handler.removeCallbacksAndMessages(null)
        bleManager.setWriteCallback(null)

        currentFile?.let { file ->
            _uploadProgress.value = UploadProgress(
                fileName = file.name,
                totalSize = file.length(),
                uploadedSize = totalSent,
                progress = if (file.length() > 0) ((totalSent * 100) / file.length()).toInt() else 0,
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