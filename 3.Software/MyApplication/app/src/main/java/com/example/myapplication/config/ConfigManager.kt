// 位置: com/example/myapplication/config/ConfigManager.kt
package com.example.myapplication.config

import android.content.Context
import java.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

// 配置文件数据类
@Serializable
data class AppConfig(
    val server: ServerConfig = ServerConfig(),
    val api: ApiConfig = ApiConfig()
)

@Serializable
data class ServerConfig(
    var ip: String = "127.0.0.1",
    var port: String = "8080"
)

@Serializable
data class ApiConfig(
    var baiduApiKey: String = "",
    var baiduSecretKey: String = "",
    var youdaoApiKey: String = "",
    var youdaoSecretKey: String = "",
    var aiKey: String = ""
)

// 配置文件管理器
object ConfigManager {
    private const val CONFIG_FILE_NAME = "app_config.json"
    private var config: AppConfig? = null

    // 初始化配置（在应用启动时调用）
    fun initialize(context: Context) {
        config = loadConfig(context)
    }

    // 获取配置
    fun getConfig(): AppConfig {
        return config ?: AppConfig()
    }

    // 保存配置
    fun saveConfig(context: Context, newConfig: AppConfig) {
        config = newConfig
        saveConfigToFile(context, newConfig)
    }

    // 更新服务器配置
    fun updateServerConfig(context: Context, serverConfig: ServerConfig) {
        val current = getConfig().copy(server = serverConfig)
        saveConfig(context, current)
    }

    // 更新API配置
    fun updateApiConfig(context: Context, apiConfig: ApiConfig) {
        val current = getConfig().copy(api = apiConfig)
        saveConfig(context, current)
    }

    // 私有方法：从文件加载配置
    private fun loadConfig(context: Context): AppConfig {
        return try {
            val file = File(context.filesDir, CONFIG_FILE_NAME)
            if (file.exists()) {
                val jsonString = file.readText()
                Json.decodeFromString<AppConfig>(jsonString)
            } else {
                // 文件不存在，创建默认配置
                val defaultConfig = AppConfig()
                saveConfigToFile(context, defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            e.printStackTrace()
            AppConfig() // 出错时返回默认配置
        }
    }

    // 私有方法：保存配置到文件
    private fun saveConfigToFile(context: Context, config: AppConfig) {
        try {
            val file = File(context.filesDir, CONFIG_FILE_NAME)
            val jsonString = Json.encodeToString(config)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}