package com.example.myapplication

import android.annotation.SuppressLint
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
import com.example.myapplication.network.PendingCommand
import kotlinx.coroutines.*

/**
 * ✅ 加速版本的CommandPollingService
 *
 * 轮询间隔调整：
 * - 原来：10000ms (10秒) - 太慢
 * - 现在：2000ms (2秒) - 快速响应
 *
 * 可根据需要调整：
 * - 1000ms = 超快（每秒查询一次，耗电量大）
 * - 2000ms = 快速（推荐）
 * - 3000ms = 中等
 * - 5000ms = 标准
 */
class CommandPollingService : Service() {

    companion object {
        private const val TAG = "CommandPollingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "command_polling_channel"

        // ✅ 修改：轮询间隔从10秒改为2秒
        private const val POLLING_INTERVAL = 2000L  // 2秒轮询一次（推荐）

        // 如果需要更快，改为：
        // private const val POLLING_INTERVAL = 1000L  // 1秒轮询一次（超快，耗电）

        private const val CLIENT_ID = "AR_glass_client"
        private const val FEATURE_WRITE_DELAY = 100L  // 特征写入延迟
    }

    private val binder = LocalBinder()
    private var pollingJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): CommandPollingService = this@CommandPollingService
    }

    @SuppressLint("ForegroundServiceType")
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
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        startPolling()
    }

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

    private fun startPolling() {
        pollingJob = CoroutineScope(Dispatchers.Default).launch {
            Log.d(TAG, "📡 开始轮询服务器命令 (间隔: ${POLLING_INTERVAL}ms)...")

            while (isActive) {
                try {
                    withContext(Dispatchers.IO) {
                        NetworkManager.getPendingCommands(
                            clientId = CLIENT_ID,
                            onSuccess = { commands ->
                                if (commands.isNotEmpty()) {
                                    Log.d(TAG, "✅ 收到 ${commands.size} 条命令")
                                    processCommands(commands)
                                } else {
                                    Log.d(TAG, "📭 暂无待处理命令")
                                }
                            },
                            onFailure = { error ->
                                Log.w(TAG, "⚠️ 轮询失败: $error")
                            }
                        )
                    }
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

    private fun processCommands(commands: List<PendingCommand>) {
        for (command in commands) {
            Log.d(TAG, "🔄 处理命令: type=${command.type}, file=${command.file_name}")

            try {
                when (command.type) {
                    "display_novel" -> {
                        Log.d(TAG, "📖 显示小说: ${command.file_name}")
                        sendDisplayNovelCommand(command.file_name)
                    }
                    "display_music" -> {
                        Log.d(TAG, "🎵 显示音乐: ${command.file_name}")
                        sendDisplayMusicCommand(command.file_name)
                    }
                    "play_music" -> {
                        Log.d(TAG, "▶️ 播放音乐: ${command.file_name}")
                        sendPlayMusicCommand(command.file_name)
                    }
                    "stop_music" -> {
                        Log.d(TAG, "⏹️ 停止播放")
                        sendStopMusicCommand()
                    }
                    else -> {
                        Log.w(TAG, "⚠️ 未知命令: ${command.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 处理命令异常: ${e.message}", e)
            }
        }
    }

    /**
     * ✅ 发送显示小说命令
     * 路径格式：/novel/novel_name.txt
     */
    private fun sendDisplayNovelCommand(novelName: String) {
        try {
            Log.d(TAG, "📥 处理显示小说命令: $novelName")

            // ✅ 添加路径前缀
            val fullPath = "/novel/$novelName"
            Log.d(TAG, "完整路径: $fullPath")

            // ==================== Step 1: 发送文件名到特征1_3 ====================
            Log.d(TAG, "Step 1️⃣: 发送文件名到特征1_3")
            Log.d(TAG, "📝 文件名: $fullPath")

            val result1 = MainActivity.bleManager.sendFileName(fullPath)
            if (!result1) {
                Log.e(TAG, "❌ 文件名写入失败，操作中止")
                return
            }
            Log.d(TAG, "✅ 文件名已写入特征1_3")

            // ==================== Step 2: 等待100ms ====================
            Log.d(TAG, "⏳ 等待${FEATURE_WRITE_DELAY}ms...")
            Thread.sleep(FEATURE_WRITE_DELAY)
            Log.d(TAG, "✅ 等待完成")

            // ==================== Step 3: 发送控制命令到特征3_2 ====================
            Log.d(TAG, "Step 2️⃣: 发送控制命令到特征3_2")
            Log.d(TAG, "🎮 命令: display_txt")

            val result2 = MainActivity.bleManager.sendControlCommand("display_txt")
            if (!result2) {
                Log.e(TAG, "❌ 控制命令写入失败")
                return
            }
            Log.d(TAG, "✅ 控制命令已写入特征3_2")
            Log.d(TAG, "🎉 显示小说命令全部发送成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送显示小说命令异常: ${e.message}", e)
        }
    }

    /**
     * ✅ 发送显示音乐命令
     * 路径格式：/music/music.json
     */
    private fun sendDisplayMusicCommand(musicName: String) {
        try {
            Log.d(TAG, "📥 处理显示音乐命令: $musicName")

            // ✅ 添加路径前缀
            val fullPath = "/music/$musicName"
            Log.d(TAG, "完整路径: $fullPath")

            Log.d(TAG, "Step 1️⃣: 发送文件名到特征1_3")
            Log.d(TAG, "📝 文件名: $fullPath")

            val result1 = MainActivity.bleManager.sendFileName(fullPath)
            if (!result1) {
                Log.e(TAG, "❌ 文件名写入失败，操作中止")
                return
            }
            Log.d(TAG, "✅ 文件名已写入特征1_3")

            Log.d(TAG, "⏳ 等待${FEATURE_WRITE_DELAY}ms...")
            Thread.sleep(FEATURE_WRITE_DELAY)
            Log.d(TAG, "✅ 等待完成")

            Log.d(TAG, "Step 2️⃣: 发送控制命令到特征3_2")
            Log.d(TAG, "🎮 命令: display_json")

            val result2 = MainActivity.bleManager.sendControlCommand("display_json")
            if (!result2) {
                Log.e(TAG, "❌ 控制命令写入失败")
                return
            }
            Log.d(TAG, "✅ 控制命令已写入特征3_2")
            Log.d(TAG, "🎉 显示音乐命令全部发送成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送显示音乐命令异常: ${e.message}", e)
        }
    }

    /**
     * ✅ 发送播放音乐命令
     * 路径格式：/music/music.mp3
     */
    private fun sendPlayMusicCommand(musicName: String) {
        try {
            Log.d(TAG, "📥 处理播放音乐命令: $musicName")

            // ✅ 添加路径前缀
            val fullPath = "/music/$musicName"
            Log.d(TAG, "完整路径: $fullPath")

            Log.d(TAG, "Step 1️⃣: 发送文件名到特征1_3")
            Log.d(TAG, "📝 文件名: $fullPath")

            val result1 = MainActivity.bleManager.sendFileName(fullPath)
            if (!result1) {
                Log.e(TAG, "❌ 文件名写入失败，操作中止")
                return
            }
            Log.d(TAG, "✅ 文件名已写入特征1_3")

            Log.d(TAG, "⏳ 等待${FEATURE_WRITE_DELAY}ms...")
            Thread.sleep(FEATURE_WRITE_DELAY)
            Log.d(TAG, "✅ 等待完成")

            Log.d(TAG, "Step 2️⃣: 发送控制命令到特征3_2")
            Log.d(TAG, "🎮 命令: play_music")

            val result2 = MainActivity.bleManager.sendControlCommand("play_music")
            if (!result2) {
                Log.e(TAG, "❌ 控制命令写入失败")
                return
            }
            Log.d(TAG, "✅ 控制命令已写入特征3_2")
            Log.d(TAG, "🎉 播放音乐命令全部发送成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送播放音乐命令异常: ${e.message}", e)
        }
    }

    /**
     * ✅ 发送停止音乐命令
     * 注意：stop_music不需要文件名，直接发送命令
     */
    private fun sendStopMusicCommand() {
        try {
            Log.d(TAG, "📥 处理停止播放命令")
            Log.d(TAG, "发送停止命令到特征3_2")
            Log.d(TAG, "🎮 命令: stop_music")

            val result = MainActivity.bleManager.sendControlCommand("stop_music")
            if (!result) {
                Log.e(TAG, "❌ 停止命令写入失败")
                return
            }
            Log.d(TAG, "✅ 停止命令已写入特征3_2")
            Log.d(TAG, "🎉 停止播放命令发送成功")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送停止音乐命令异常: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "▶️ 服务启动命令")
        return START_STICKY
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