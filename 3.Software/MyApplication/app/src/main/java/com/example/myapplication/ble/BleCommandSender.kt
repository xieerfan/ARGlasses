package com.example.myapplication

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.concurrent.thread

object BleCommandSender {

    private const val TAG = "BleCommandSender"

    const val BLE_CMD_DELETE_FILE = "delete_file"
    const val BLE_CMD_DELETE_JSON = "delete_json"
    const val BLE_CMD_PLAY_MP3 = "play_mp3"
    const val BLE_CMD_STOP_MP3 = "stop_mp3"
    const val BLE_CMD_DISPLAY_TXT = "display_txt"
    const val BLE_CMD_DISPLAY_JSON = "display_json"
    const val BLE_CMD_NEXT_PAGE = "next_page"
    const val BLE_CMD_PRE_PAGE = "pre_page"
    const val BLE_CMD_VOL_UP = "vol_up"
    const val BLE_CMD_VOL_DOWN = "vol_down"
    const val BLE_CMD_SET_POWER = "set_power"

    // ==================== 文件上传 ====================

    /**
     * ✅ 发送文件数据（用于文件上传）
     * 完整流程：文件名(1_3) → start(1_2) → 数据(1_1) → end(1_2)
     *
     * ⚠️ 关键修复：确保使用正确的特征和方法
     */
    fun uploadFileData(fileData: ByteArray, fileName: String, onComplete: (() -> Unit)? = null): Boolean {
        val handler = Handler(Looper.getMainLooper())

        // 在后台线程执行，避免阻塞主线程
        thread {
            try {
                val bleManager = MainActivity.bleManager

                if (!bleManager.isConnected.value) {
                    Log.w(TAG, "⚠️ BLE 未连接，无法上传文件")
                    handler.post {
                        onComplete?.invoke()
                    }
                    return@thread
                }

                Log.d(TAG, "📤 开始上传文件: $fileName")

                // ==================== Step 1: 发送文件名到特征1_3 ====================
                Log.d(TAG, "Step 1️⃣: 发送文件名到特征1_3...")
                Log.d(TAG, "📝 文件名: $fileName")

                val fileNameResult = bleManager.sendFileName(fileName)
                if (!fileNameResult) {
                    Log.e(TAG, "❌ 文件名发送失败，操作中止")
                    handler.post { onComplete?.invoke() }
                    return@thread
                }
                Log.d(TAG, "✅ 文件名已写入特征1_3")
                Thread.sleep(200)

                // ==================== Step 2: 发送start命令到特征1_2 ====================
                Log.d(TAG, "Step 2️⃣: 发送start命令到特征1_2...")
                Log.d(TAG, "🎮 命令: start")

                // ⚠️ 关键：这里必须调用 sendFileControl()，而不是 sendControlCommand()！
                val startResult = bleManager.sendFileControl("start")
                if (!startResult) {
                    Log.e(TAG, "❌ start命令发送失败，操作中止")
                    handler.post { onComplete?.invoke() }
                    return@thread
                }
                Log.d(TAG, "✅ start命令已写入特征1_2")
                Thread.sleep(200)

                // ==================== Step 3: 分块发送数据到特征1_1 ====================
                Log.d(TAG, "Step 3️⃣: 分块发送文件数据到特征1_1...")
                val chunkSize = 400
                var sentBytes = 0
                var chunkCount = 0

                while (sentBytes < fileData.size) {
                    if (!bleManager.isConnected.value) {
                        Log.e(TAG, "❌ BLE 连接已断开")
                        handler.post {
                            onComplete?.invoke()
                        }
                        return@thread
                    }

                    val currentChunkSize = Math.min(chunkSize, fileData.size - sentBytes)
                    val chunk = fileData.sliceArray(sentBytes until sentBytes + currentChunkSize)

                    // ⚠️ 关键：直接调用 sendFileData()，真正写入特征1_1
                    val chunkResult = bleManager.sendFileData(chunk)
                    if (!chunkResult) {
                        Log.e(TAG, "❌ 数据块 ${chunkCount + 1} 发送失败，操作中止")
                        handler.post { onComplete?.invoke() }
                        return@thread
                    }

                    sentBytes += currentChunkSize
                    chunkCount++
                    Log.d(TAG, "📤 数据块 $chunkCount: $currentChunkSize 字节 (总计: $sentBytes / ${fileData.size})")

                    Thread.sleep(60)
                }

                Log.d(TAG, "✅ 全部 $chunkCount 个数据块已发送")

                // ==================== Step 4: 发送end命令到特征1_2 ====================
                Log.d(TAG, "Step 4️⃣: 发送end命令到特征1_2...")
                Log.d(TAG, "🎮 命令: end")
                Thread.sleep(200)

                // ⚠️ 关键：这里必须调用 sendFileControl()，而不是 sendControlCommand()！
                val endResult = bleManager.sendFileControl("end")
                if (!endResult) {
                    Log.e(TAG, "❌ end命令发送失败，操作中止")
                    handler.post { onComplete?.invoke() }
                    return@thread
                }
                Log.d(TAG, "✅ end命令已写入特征1_2")

                Log.d(TAG, "🎉 文件上传完成！")

                // ==================== Step 5: 调用完成回调 ====================
                Thread.sleep(500)
                handler.post {
                    Log.d(TAG, "📢 调用上传完成回调")
                    onComplete?.invoke()
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 上传文件异常: ${e.message}", e)
                e.printStackTrace()
                handler.post {
                    onComplete?.invoke()
                }
            }
        }

        return true
    }

    // ==================== 控制命令 ====================

    fun deleteMusic(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除音乐: $fileName")
        MainActivity.bleManager.sendFileName(fileName)
        MainActivity.bleManager.sendControlCommand("delete_file")
        return true
    }

    fun deleteNovel(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除小说: $fileName")
        MainActivity.bleManager.sendFileName(fileName)
        MainActivity.bleManager.sendControlCommand("delete_file")
        return true
    }

    fun deleteJson(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除 JSON: $fileName")
        MainActivity.bleManager.sendControlCommand("delete_json")
        return true
    }

    fun playMp3(fileName: String): Boolean {
        Log.d(TAG, "▶️ 播放音乐: $fileName")
        MainActivity.bleManager.sendFileName(fileName)
        MainActivity.bleManager.sendControlCommand("play_mp3")
        return true
    }

    fun stopMp3(): Boolean {
        Log.d(TAG, "⏹️ 停止播放")
        MainActivity.bleManager.sendControlCommand("stop_mp3")
        return true
    }

    fun displayText(fileName: String): Boolean {
        Log.d(TAG, "📄 显示文本: $fileName")
        MainActivity.bleManager.sendFileName(fileName)
        MainActivity.bleManager.sendControlCommand("display_txt")
        return true
    }

    fun displayJson(fileName: String): Boolean {
        Log.d(TAG, "📊 显示 JSON: $fileName")
        MainActivity.bleManager.sendFileName(fileName)
        MainActivity.bleManager.sendControlCommand("display_json")
        return true
    }

    fun nextPage(): Boolean {
        Log.d(TAG, "➡️ 下一页")
        MainActivity.bleManager.sendControlCommand("next_page")
        return true
    }

    fun previousPage(): Boolean {
        Log.d(TAG, "⬅️ 上一页")
        MainActivity.bleManager.sendControlCommand("pre_page")
        return true
    }

    fun volumeUp(): Boolean {
        Log.d(TAG, "🔊 音量增加")
        MainActivity.bleManager.sendControlCommand("vol_up")
        return true
    }

    fun volumeDown(): Boolean {
        Log.d(TAG, "🔉 音量降低")
        MainActivity.bleManager.sendControlCommand("vol_down")
        return true
    }

    fun setChargingCurrent(currentValue: Int): Boolean {
        Log.d(TAG, "⚡ 设置充电电流: $currentValue")
        MainActivity.bleManager.sendFileName(currentValue.toString())
        MainActivity.bleManager.sendControlCommand("set_power")
        return true
    }
}