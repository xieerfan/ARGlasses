package com.example.myapplication

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