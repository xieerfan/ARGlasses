// 位置: com/example/myapplication/BleCommandSender.kt
// 用途: 向 ESP32 发送各种 BLE 命令（删除文件等）

package com.example.myapplication

import android.util.Log

/**
 * ✅ BLE 命令发送器
 *
 * 用于向 ESP32 发送各种控制命令
 * 命令协议：
 * 1. 发送文件名到特征 1_3 (pCharacteristic1_3)
 * 2. 发送命令到特征 3_2 (pCharacteristic3_2)
 *
 * 示例：删除文件
 * - sendCommand(BLE_CMD_DELETE_FILE, "music_file.mp3")
 */
object BleCommandSender {

    private const val TAG = "BleCommandSender"

    // ✅ 命令定义（与 ESP32 对应）
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

    /**
     * ✅ 发送文件操作命令
     *
     * @param command 命令字符串（如 "delete_file", "play_mp3" 等）
     * @param fileName 文件名（如 "music_file.mp3" 或 "novel_file.txt"）
     * @return 成功返回 true，失败返回 false
     */
    fun sendFileCommand(command: String, fileName: String): Boolean {
        try {
            val bleManager = MainActivity.bleManager

            if (!bleManager.isConnected.value) {
                Log.w(TAG, "⚠️ BLE 未连接，无法发送命令")
                return false
            }

            // 第1步：发送文件名到特征 1_3
            sendFileName(fileName)

            // 第2步：延迟 50ms，确保文件名已接收
            Thread.sleep(50)

            // 第3步：发送命令到特征 3_2
            bleManager.sendCommand(command)

            Log.d(TAG, "📤 已发送命令: $command, 文件: $fileName")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送命令失败: ${e.message}", e)
            return false
        }
    }

    /**
     * ✅ 删除音乐文件
     */
    fun deleteMusic(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除音乐: $fileName")
        return sendFileCommand(BLE_CMD_DELETE_FILE, fileName)
    }

    /**
     * ✅ 删除小说文件
     */
    fun deleteNovel(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除小说: $fileName")
        return sendFileCommand(BLE_CMD_DELETE_FILE, fileName)
    }

    /**
     * ✅ 删除 JSON 文件
     */
    fun deleteJson(fileName: String): Boolean {
        Log.d(TAG, "🗑️ 删除 JSON: $fileName")
        return sendFileCommand(BLE_CMD_DELETE_JSON, fileName)
    }

    /**
     * ✅ 播放 MP3
     */
    fun playMp3(fileName: String): Boolean {
        Log.d(TAG, "▶️ 播放音乐: $fileName")
        return sendFileCommand(BLE_CMD_PLAY_MP3, fileName)
    }

    /**
     * ✅ 停止播放
     */
    fun stopMp3(): Boolean {
        Log.d(TAG, "⏹️ 停止播放")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        bleManager.sendCommand(BLE_CMD_STOP_MP3)
        return true
    }

    /**
     * ✅ 显示文本文件
     */
    fun displayText(fileName: String): Boolean {
        Log.d(TAG, "📄 显示文本: $fileName")
        return sendFileCommand(BLE_CMD_DISPLAY_TXT, fileName)
    }

    /**
     * ✅ 显示 JSON 文件
     */
    fun displayJson(fileName: String): Boolean {
        Log.d(TAG, "📊 显示 JSON: $fileName")
        return sendFileCommand(BLE_CMD_DISPLAY_JSON, fileName)
    }

    /**
     * ✅ 下一页
     */
    fun nextPage(): Boolean {
        Log.d(TAG, "➡️ 下一页")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        bleManager.sendCommand(BLE_CMD_NEXT_PAGE)
        return true
    }

    /**
     * ✅ 上一页
     */
    fun previousPage(): Boolean {
        Log.d(TAG, "⬅️ 上一页")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        bleManager.sendCommand(BLE_CMD_PRE_PAGE)
        return true
    }

    /**
     * ✅ 音量增加
     */
    fun volumeUp(): Boolean {
        Log.d(TAG, "🔊 音量增加")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        bleManager.sendCommand(BLE_CMD_VOL_UP)
        return true
    }

    /**
     * ✅ 音量降低
     */
    fun volumeDown(): Boolean {
        Log.d(TAG, "🔉 音量降低")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        bleManager.sendCommand(BLE_CMD_VOL_DOWN)
        return true
    }

    /**
     * ✅ 设置电源
     */
    fun setPower(value: String): Boolean {
        Log.d(TAG, "⚡ 设置电源: $value")
        val bleManager = MainActivity.bleManager
        if (!bleManager.isConnected.value) {
            Log.w(TAG, "⚠️ BLE 未连接")
            return false
        }
        sendFileName(value)
        Thread.sleep(50)
        bleManager.sendCommand(BLE_CMD_SET_POWER)
        return true
    }

    /**
     * ✅ 发送文件名
     *
     * 这个函数需要调用 BleManager 中的方法来发送数据到特征 1_3
     */
    private fun sendFileName(fileName: String) {
        try {
            // ✅ 这里需要通过 BLE 发送文件名
            // 实现方式：通过 BleManager 的接口发送到特征 1_3
            //
            // 示例（需要在 BleManager 中添加此方法）：
            // bleManager.sendToCharacteristic(fileName, pCharacteristic1_3)

            val bleManager = MainActivity.bleManager
            bleManager.sendCommand(fileName)  // 临时方案，需要改进

            Log.d(TAG, "📝 已发送文件名: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送文件名失败: ${e.message}", e)
        }
    }
}