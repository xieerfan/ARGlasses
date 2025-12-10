package com.example.myapplication

import java.util.UUID

object BleConstants {
    const val DEVICE_NAME = "AR_GLASS"

    val SERVICE_2 = UUID.fromString("aabb0200-0000-1000-8000-00805f9b34fb")
    val SERVICE_3 = UUID.fromString("aabb0300-0000-1000-8000-00805f9b34fb")

    val CHAR_IMAGE_LEN = UUID.fromString("aabb0201-0000-1000-8000-00805f9b34fb")
    val CHAR_IMAGE_CMD = UUID.fromString("aabb0202-0000-1000-8000-00805f9b34fb")
    val CHAR_IMAGE_DATA = UUID.fromString("aabb0203-0000-1000-8000-00805f9b34fb")

    val CHAR_DATA_NOTIFY = UUID.fromString("aabb0303-0000-1000-8000-00805f9b34fb")

    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}