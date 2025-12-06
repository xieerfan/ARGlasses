package com.example.myapplication

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    data class BleDevice(
        val name: String,
        val address: String
    )

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var imageCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null
    private var statusNotificationCharacteristic: BluetoothGattCharacteristic? = null

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionState = MutableStateFlow<String>("未连接")
    val connectionState: StateFlow<String> = _connectionState

    private val _receivedImage = MutableStateFlow<ByteArray?>(null)
    val receivedImage: StateFlow<ByteArray?> = _receivedImage

    private val _transferProgress = MutableStateFlow<String>("")
    val transferProgress: StateFlow<String> = _transferProgress

    // 🆕 添加接收命令的StateFlow
    private val _receivedCommand = MutableStateFlow<String?>(null)
    val receivedCommand: StateFlow<String?> = _receivedCommand

    private var imageBuffer = mutableListOf<Byte>()
    private var expectedImageSize = 0
    private var isReceivingImage = false

    private var isFullyInitialized = false
    private var notificationsEnabled = false
    private var mtuNegotiated = false

    private val deviceMap = mutableMapOf<String, android.bluetooth.BluetoothDevice>()

    private val handler = Handler(Looper.getMainLooper())

    private var lastDataReceivedTime = 0L
    private var currentChunkBuffer = mutableListOf<Byte>()

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())
        val newLog = "[$timestamp] $message"
        _logs.value = (_logs.value + newLog).takeLast(100)
        Log.d(TAG, message)
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        addLog("📡 开始扫描BLE设备...")
        _devices.value = emptyList()
        deviceMap.clear()

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        scanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        addLog("⏹️ 停止扫描")
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                if (it.device.name == BleConstants.DEVICE_NAME) {
                    val bleDevice = BleDevice(
                        name = it.device.name ?: "未知设备",
                        address = it.device.address
                    )

                    val currentDevices = _devices.value.toMutableList()
                    if (!currentDevices.any { d -> d.address == bleDevice.address }) {
                        currentDevices.add(bleDevice)
                        _devices.value = currentDevices
                        deviceMap[bleDevice.address] = it.device
                        addLog("✅ 发现设备: ${bleDevice.name} (${bleDevice.address})")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val device = deviceMap[address]
        if (device != null) {
            connect(device)
        } else {
            addLog("⚠️ 未找到设备: $address")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: android.bluetooth.BluetoothDevice) {
        addLog("🔗 正在连接 ${device.name}...")
        _connectionState.value = "连接中..."
        _isConnected.value = false
        isFullyInitialized = false
        notificationsEnabled = false
        mtuNegotiated = false

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        addLog("🔌 断开连接")
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = "未连接"
        _isConnected.value = false
        isFullyInitialized = false
        notificationsEnabled = false
        mtuNegotiated = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("✅ 已连接，协商MTU...")
                    _connectionState.value = "已连接"
                    handler.postDelayed({
                        gatt?.requestMtu(512)
                    }, 300)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addLog("❌ 连接断开")
                    _connectionState.value = "未连接"
                    _isConnected.value = false
                    isFullyInitialized = false
                    notificationsEnabled = false
                    mtuNegotiated = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("✅ MTU协商成功: $mtu 字节 (可用载荷: ${mtu - 3} 字节)")
                mtuNegotiated = true
                handler.postDelayed({
                    gatt?.discoverServices()
                }, 300)
            } else {
                addLog("⚠️ MTU协商失败，使用默认MTU 23字节")
                mtuNegotiated = true
                handler.postDelayed({
                    gatt?.discoverServices()
                }, 300)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("🔍 发现服务，正在初始化...")

                val service2 = gatt?.getService(BleConstants.SERVICE_2)
                imageCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_LEN)
                commandCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_CMD)
                notificationCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_DATA)

                val service3 = gatt?.getService(BleConstants.SERVICE_3)
                statusNotificationCharacteristic = service3?.getCharacteristic(BleConstants.CHAR_DATA_NOTIFY)

                if (notificationCharacteristic != null) {
                    gatt?.setCharacteristicNotification(notificationCharacteristic, true)
                    val descriptor = notificationCharacteristic?.getDescriptor(BleConstants.CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                    addLog("🔔 启用图片数据通知 (0203)")
                } else {
                    addLog("⚠️ 图片数据通知特征不可用")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("✅ 描述符写入成功")

                if (!notificationsEnabled && statusNotificationCharacteristic != null) {
                    gatt?.setCharacteristicNotification(statusNotificationCharacteristic, true)
                    val descriptor = statusNotificationCharacteristic?.getDescriptor(BleConstants.CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                    addLog("🔔 启用状态通知 (0303)")
                    notificationsEnabled = true
                } else {
                    isFullyInitialized = true
                    _isConnected.value = true
                    addLog("🎉 初始化完成，可以开始传输")
                }
            } else {
                addLog("⚠️ 描述符写入失败: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.value?.let { data ->
                    if (characteristic.uuid == BleConstants.CHAR_IMAGE_LEN) {
                        expectedImageSize = byteArrayToInt(data)
                        addLog("📦 图片大小: $expectedImageSize 字节")
                        _transferProgress.value = "准备接收 $expectedImageSize 字节"

                        imageBuffer.clear()
                        currentChunkBuffer.clear()
                        isReceivingImage = true
                        lastDataReceivedTime = System.currentTimeMillis()

                        handler.postDelayed({
                            requestImageData()
                        }, 100)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.value?.let { data ->
                when (characteristic.uuid) {
                    BleConstants.CHAR_IMAGE_DATA -> {
                        if (expectedImageSize > 0 && imageBuffer.size < expectedImageSize) {
                            currentChunkBuffer.addAll(data.toList())
                            lastDataReceivedTime = System.currentTimeMillis()

                            handler.removeCallbacks(chunkCompleteChecker)
                            handler.postDelayed(chunkCompleteChecker, 30)
                        } else {

                        }
                    }
                    BleConstants.CHAR_DATA_NOTIFY -> {
                        val message = String(data, Charsets.UTF_8)
                        addLog("📢 收到通知: $message")

                        when (message) {
                            "image_ready" -> {
                                addLog("🎉 图片已准备就绪，开始读取...")
                                handler.postDelayed({
                                    readImageLength()
                                }, 50)
                            }
                            "image_end" -> {
                                addLog("💾 传输完成信号")
                            }
                            "ai_work" -> {
                                // 🆕 处理ai_work命令
                                addLog("🤖 收到AI处理命令")
                                _receivedCommand.value = "ai_work"

                                // 自动开始读取图片
                                handler.postDelayed({
                                    readImageLength()
                                }, 100)
                            }
                            else -> {
                                // 🆕 处理其他可能的命令
                                if (message.isNotEmpty()) {
                                    addLog("📨 收到命令: $message")
                                    _receivedCommand.value = message
                                } else {

                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    private val chunkCompleteChecker = Runnable {
        if (currentChunkBuffer.isNotEmpty()) {
            imageBuffer.addAll(currentChunkBuffer)
            val chunkSize = currentChunkBuffer.size
            currentChunkBuffer.clear()

            val progress = (imageBuffer.size * 100 / expectedImageSize)
            _transferProgress.value = "接收中 $progress% (${imageBuffer.size}/$expectedImageSize)"
            addLog("接收块完成: $chunkSize 字节, 总进度 $progress% (${imageBuffer.size}/$expectedImageSize)")

            if (imageBuffer.size >= expectedImageSize) {
                addLog("✅ 图片接收完成")
                _receivedImage.value = imageBuffer.toByteArray()
                isReceivingImage = false
                _transferProgress.value = ""
            } else {
                handler.postDelayed({
                    requestImageData()
                }, 50)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化，请等待...")
            return
        }

        commandCharacteristic?.let { char ->
            char.value = command.toByteArray()
            bluetoothGatt?.writeCharacteristic(char)
            addLog("📤 发送命令: $command")
        }
    }

    @SuppressLint("MissingPermission")
    fun readImageLength() {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化，请等待...")
            return
        }

        imageCharacteristic?.let {
            bluetoothGatt?.readCharacteristic(it)
            addLog("📖 读取图片长度...")
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestImageData() {
        sendCommand("getimage")
    }

    private fun byteArrayToInt(bytes: ByteArray): Int {
        return if (bytes.size >= 4) {
            (bytes[0].toInt() and 0xFF) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[3].toInt() and 0xFF) shl 24)
        } else 0
    }

    fun isImageReadyForTransfer(): Boolean {
        return isFullyInitialized && commandCharacteristic != null
    }

    // 🆕 清除接收到的命令
    fun clearReceivedCommand() {
        _receivedCommand.value = null
    }
}