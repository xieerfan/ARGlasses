// 位置: com/example/myapplication/ble/BleConstants.kt
package com.example.myapplication.ble

import java.util.UUID

object BleConstants {
    const val DEVICE_NAME = "AR_GLASS"

    // Service 1: 文件接收（ESP32已实现）
    val SERVICE_1 = UUID.fromString("aabb0100-0000-1000-8000-00805f9b34fb")
    val CHAR_FILE_DATA = UUID.fromString("aabb0101-0000-1000-8000-00805f9b34fb")     // 数据
    val CHAR_FILE_CONTROL = UUID.fromString("aabb0102-0000-1000-8000-00805f9b34fb")  // 控制(start/update/end)
    val CHAR_FILE_NAME = UUID.fromString("aabb0103-0000-1000-8000-00805f9b34fb")     // 文件名

    // Service 2: 照片发送
    val SERVICE_2 = UUID.fromString("aabb0200-0000-1000-8000-00805f9b34fb")
    val CHAR_IMAGE_LEN = UUID.fromString("aabb0201-0000-1000-8000-00805f9b34fb")
    val CHAR_IMAGE_CMD = UUID.fromString("aabb0202-0000-1000-8000-00805f9b34fb")
    val CHAR_IMAGE_DATA = UUID.fromString("aabb0203-0000-1000-8000-00805f9b34fb")

    // Service 3: 数据获取
    val SERVICE_3 = UUID.fromString("aabb0300-0000-1000-8000-00805f9b34fb")
    val CHAR_BATTERY = UUID.fromString("aabb0301-0000-1000-8000-00805f9b34fb")
    val CHAR_DATA_IN = UUID.fromString("aabb0302-0000-1000-8000-00805f9b34fb")
    val CHAR_DATA_NOTIFY = UUID.fromString("aabb0303-0000-1000-8000-00805f9b34fb")

    // CCCD
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
