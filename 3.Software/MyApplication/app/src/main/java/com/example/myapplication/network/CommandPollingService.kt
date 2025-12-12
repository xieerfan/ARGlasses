package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.network.NetworkManager
import kotlinx.coroutines.*

/**
 * ✅ 命令轮询后台服务
 *
 * 功能：
 * 1. 在APP启动后运行
 * 2. 每10秒轮询一次服务器获取待处理命令
 * 3. 接收到显示小说/音乐命令后，发送BLE命令到设备
 * 4. 支持前台服务（不会被杀死）
 */
class CommandPollingService : Service() {

    companion object {
        private const val TAG = "CommandPollingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "command_polling_channel"
        private const val POLLING_INTERVAL = 10000L  // 10秒轮询一次
    }

    private val binder = LocalBinder()
    private var pollingJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): CommandPollingService = this@CommandPollingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 服务创建")

        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        // 启动为前台服务
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("命令轮询服务")
            .setContentText("正在监听服务器命令...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 启动轮询任务
        startPolling()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "命令轮询",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "APP后台轮询服务器命令"

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 启动轮询任务
     */
    private fun startPolling() {
        pollingJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "📡 开始轮询服务器命令...")

            while (isActive) {
                try {
                    // 获取待处理命令
                    withContext(Dispatchers.IO) {
                        NetworkManager.getPendingCommands(
                            clientId = "AR_glass_client",
                            onSuccess = { commands ->
                                if (commands.isNotEmpty()) {
                                    Log.d(TAG, "✅ 收到 ${commands.size} 条命令")
                                    processCommands(commands)
                                }
                            },
                            onFailure = { error ->
                                Log.w(TAG, "⚠️ 轮询失败: $error")
                            }
                        )
                    }

                    // 等待10秒后继续轮询
                    delay(POLLING_INTERVAL)

                } catch (e: CancellationException) {
                    Log.d(TAG, "轮询任务已取消")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 轮询异常: ${e.message}", e)
                    delay(POLLING_INTERVAL)
                }
            }
        }
    }

    /**
     * 处理收到的命令
     */
    private fun processCommands(commands: List<Map<String, Any>>) {
        for (command in commands) {
            val type = command["type"] as? String ?: continue
            val fileName = command["file_name"] as? String ?: continue

            Log.d(TAG, "🔄 处理命令: $type - $fileName")

            try {
                when (type) {
                    "display_novel" -> {
                        Log.d(TAG, "📖 显示小说: $fileName")
                        BleCommandSender.displayText(fileName)
                    }
                    "display_music" -> {
                        Log.d(TAG, "🎵 显示音乐: $fileName")
                        BleCommandSender.displayJson(fileName)
                    }
                    "play_music" -> {
                        Log.d(TAG, "▶️ 播放音乐: $fileName")
                        BleCommandSender.playMp3(fileName)
                    }
                    "stop_music" -> {
                        Log.d(TAG, "⏹️ 停止播放")
                        BleCommandSender.stopMp3()
                    }
                    else -> {
                        Log.w(TAG, "⚠️ 未知命令: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理命令失败: ${e.message}", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ 服务启动命令")
        return START_STICKY  // 被杀死后自动重启
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "⏹️ 服务销毁")
        pollingJob?.cancel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}