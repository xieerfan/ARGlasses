package com.example.myapplication

// 上传文件信息
data class UploadFileInfo(
    val fileName: String,
    val fileSize: Long,
    val filePath: String,
    val uploadTime: Long = System.currentTimeMillis()
)

// 上传进度
data class UploadProgress(
    val fileName: String,
    val totalSize: Long,
    val uploadedSize: Long,
    val progress: Int,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

// 文件类型
enum class FileType(val extension: String, val targetPath: String) {
    MUSIC("mp3", "/sdcard/music"),
    NOVEL("txt", "/sdcard/novel")
}