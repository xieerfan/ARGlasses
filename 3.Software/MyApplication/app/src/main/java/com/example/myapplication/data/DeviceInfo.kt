// 位置: com/example/myapplication/data/DeviceInfo.kt
package com.example.myapplication.data

// 设备信息数据类
data class DeviceInfo(
    val connectionState: String = "未连接",
    val deviceName: String = "未知",
    val deviceAddress: String = "未知",
    val mtuSize: Int = 23,
    val serviceCount: Int = 0,
    val characteristicCount: Int = 0,
    val descriptorCount: Int = 0,
    val characteristics: List<CharacteristicInfo> = emptyList(),
    val cccdStates: Map<String, Boolean> = emptyMap()
)

// 特征信息数据类
data class CharacteristicInfo(
    val uuid: String,
    val properties: List<String>
)

data class UploadFileInfo(
    val id: Int = 0,  // ✅ 添加 ID 字段（用于删除）
    val fileName: String,
    val fileSize: Long,
    val filePath: String = "",
    val uploadTime: Long = System.currentTimeMillis()
)

// 上传进度
data class UploadProgress(
    val fileName: String,
    val totalSize: Long,
    val uploadedSize: Long,
    val progress: Int,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null
)

// 文件类型
enum class FileType(val extension: String, val targetPath: String) {
    MUSIC("mp3", "/sdcard/music"),
    NOVEL("txt", "/sdcard/novel"),
    ANSWER("json", "/sdcard/an")  // ✅ 新增：答案文件
}

// ✅ 新增：分析答案信息
data class AnalysisAnswerInfo(
    val answerId: String,
    val fileName: String,
    val fileSize: Long,
    val subject: String,
    val content: String,
    val uploadTime: Long = System.currentTimeMillis()
)
