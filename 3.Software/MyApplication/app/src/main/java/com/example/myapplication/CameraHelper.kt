package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ✅ 修复后的相机拍照助手类
 * 处理拍照权限、拍照功能、文件管理
 *
 * 改进点：
 * 1. 更加健壮的权限检查
 * 2. 完善的文件处理
 * 3. 更好的错误处理
 * 4. 支持多种Android版本
 */
class CameraHelper(
    private val activity: ComponentActivity,
    private val onPhotoCaptured: (File) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "CameraHelper"
        private const val PHOTOS_DIR_NAME = "photos"
    }

    private var takePictureLauncher: ActivityResultLauncher<Uri>? = null
    private var photoUri: Uri? = null
    private var currentPhotoPath: String? = null

    // 在init块中初始化launcher
    init {
        try {
            setupTakePictureLauncher()
            Log.d(TAG, "CameraHelper 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "CameraHelper 初始化失败: ${e.message}", e)
        }
    }

    /**
     * 设置拍照结果处理
     */
    private fun setupTakePictureLauncher() {
        takePictureLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            Log.d(TAG, "拍照回调: success=$success, photoUri=$photoUri, photoPath=$currentPhotoPath")

            if (success) {
                try {
                    photoUri?.let { _ ->
                        currentPhotoPath?.let { path ->
                            val photoFile = File(path)
                            if (photoFile.exists()) {
                                Log.d(TAG, "✅ 拍照成功: ${photoFile.absolutePath} (大小: ${photoFile.length()} bytes)")
                                onPhotoCaptured(photoFile)
                            } else {
                                val errorMsg = "照片文件不存在: $path"
                                Log.e(TAG, errorMsg)
                                onError(errorMsg)
                            }
                        } ?: run {
                            val errorMsg = "无法获取照片路径"
                            Log.e(TAG, errorMsg)
                            onError(errorMsg)
                        }
                    } ?: run {
                        val errorMsg = "无法获取照片URI"
                        Log.e(TAG, errorMsg)
                        onError(errorMsg)
                    }
                } catch (e: Exception) {
                    val errorMsg = "处理照片时出错: ${e.message}"
                    Log.e(TAG, errorMsg, e)
                    onError(errorMsg)
                }
            } else {
                Log.w(TAG, "用户取消拍照或拍照失败")
                onError("用户取消拍照")
            }
        }
    }

    /**
     * 启动拍照（不带权限检查，外部需要先检查权限）
     */
    fun takePicture() {
        try {
            // 检查权限
            if (!hasCameraPermission()) {
                val errorMsg = "没有相机权限"
                Log.w(TAG, errorMsg)
                onError(errorMsg)
                return
            }

            // 检查launcher是否初始化
            if (takePictureLauncher == null) {
                val errorMsg = "拍照功能未正确初始化"
                Log.e(TAG, errorMsg)
                onError(errorMsg)
                return
            }

            // 创建图片文件
            val photoFile = createImageFile()
            currentPhotoPath = photoFile.absolutePath
            Log.d(TAG, "创建图片文件: $currentPhotoPath")

            // 获取 Uri
            photoUri = try {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.fileprovider",
                    photoFile
                )
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "FileProvider 配置错误: ${e.message}", e)
                onError("FileProvider 配置错误，请检查 AndroidManifest.xml")
                return
            } catch (e: Exception) {
                Log.e(TAG, "获取 FileProvider URI 失败: ${e.message}", e)
                onError("无法创建照片保存位置: ${e.message}")
                return
            }

            Log.d(TAG, "启动拍照，URI: $photoUri")

            // 启动相机应用
            takePictureLauncher?.launch(photoUri)
                ?: run {
                    onError("拍照功能不可用")
                }

        } catch (e: Exception) {
            val errorMsg = "启动拍照失败: ${e.message}"
            Log.e(TAG, errorMsg, e)
            onError(errorMsg)
        }
    }

    /**
     * 检查是否有相机权限
     */
    fun hasCameraPermission(): Boolean {
        val permission = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "检查相机权限: $permission")
        return permission
    }

    /**
     * 创建图片文件
     */
    private fun createImageFile(): File {
        val storageDir = try {
            // 优先使用外部文件目录
            val externalDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (externalDir != null) {
                externalDir
            } else {
                // 备用：使用内部存储
                File(activity.filesDir, "pictures")
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法获取外部文件目录，使用内部存储")
            File(activity.filesDir, "pictures")
        }

        // 创建 photos 子目录
        val photosDir = File(storageDir, PHOTOS_DIR_NAME)
        if (!photosDir.exists()) {
            val created = photosDir.mkdirs()
            Log.d(TAG, "创建photos目录: $created - ${photosDir.absolutePath}")
        }

        // 生成文件名
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "photo_${timeStamp}.jpg"
        val imageFile = File(photosDir, imageFileName)

        Log.d(TAG, "创建图片文件: ${imageFile.absolutePath}")
        return imageFile
    }

    /**
     * 启动拍照（带权限申请）
     */
    fun takePictureWithPermission(permissionLauncher: ActivityResultLauncher<String>) {
        Log.d(TAG, "调用 takePictureWithPermission")

        if (hasCameraPermission()) {
            Log.d(TAG, "已有相机权限，直接启动拍照")
            takePicture()
        } else {
            Log.d(TAG, "没有相机权限，请求权限...")
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                Log.e(TAG, "请求权限时出错: ${e.message}", e)
                onError("权限请求失败: ${e.message}")
            }
        }
    }
}

/**
 * ✅ API KEY状态检查工具
 */
object ApiKeyChecker {
    private const val TAG = "ApiKeyChecker"

    fun checkApiKeys(context: Context): ApiKeyStatus {
        return try {
            val config = ConfigManager.getConfig()

            val status = ApiKeyStatus(
                baiduConfigured = config.api.baiduApiKey.isNotEmpty()
                        && config.api.baiduSecretKey.isNotEmpty(),
                youdaoConfigured = config.api.youdaoApiKey.isNotEmpty()
                        && config.api.youdaoSecretKey.isNotEmpty(),
                aiConfigured = config.api.aiKey.isNotEmpty()
            )

            Log.d(TAG, "API KEY 状态检查: $status")
            status
        } catch (e: Exception) {
            Log.w(TAG, "检查API KEY失败: ${e.message}", e)
            ApiKeyStatus(false, false, false)
        }
    }
}

/**
 * ✅ API KEY配置状态
 */
data class ApiKeyStatus(
    val baiduConfigured: Boolean = false,
    val youdaoConfigured: Boolean = false,
    val aiConfigured: Boolean = false
) {
    /**
     * 是否完整配置（三个都配置了）
     */
    val isFullyConfigured: Boolean
        get() = baiduConfigured && youdaoConfigured && aiConfigured

    /**
     * 已配置的API KEY数量
     */
    val configuredCount: Int
        get() = listOf(baiduConfigured, youdaoConfigured, aiConfigured).count { it }

    /**
     * 获取配置状态的字符串表示
     */
    override fun toString(): String {
        return "ApiKeyStatus(配置: $configuredCount/3, " +
                "百度: ${if (baiduConfigured) "✓" else "✗"}, " +
                "有道: ${if (youdaoConfigured) "✓" else "✗"}, " +
                "AI: ${if (aiConfigured) "✓" else "✗"})"
    }
}