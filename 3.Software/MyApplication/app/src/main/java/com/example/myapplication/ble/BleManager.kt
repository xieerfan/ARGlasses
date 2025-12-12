package com.example.myapplication

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.myapplication.ble.BleConstants
import com.example.myapplication.data.CharacteristicInfo
import com.example.myapplication.data.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    // ==================== 数据类和接口 ====================

    data class BleDevice(
        val name: String,
        val address: String
    )

    /**
     * 写入回调接口 - 用于处理异步写入操作
     */
    interface WriteCallback {
        fun onWriteSuccess()
        fun onWriteFailure(error: String)
    }

    // ==================== 成员变量 ====================

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var bluetoothGatt: BluetoothGatt? = null

    // 写入回调
    private var writeCallback: WriteCallback? = null

    // Service 1 - 文件上传特征
    private var fileDataCharacteristic: BluetoothGattCharacteristic? = null
    private var fileControlCharacteristic: BluetoothGattCharacteristic? = null
    private var fileNameCharacteristic: BluetoothGattCharacteristic? = null

    // Service 2 - 图片传输特征
    private var imageCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null

    // Service 3 - 数据通知特征
    private var statusNotificationCharacteristic: BluetoothGattCharacteristic? = null

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: MutableStateFlow<List<String>> = _logs

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _connectionState = MutableStateFlow<String>("未连接")
    val connectionState: StateFlow<String> = _connectionState

    private val _receivedImage = MutableStateFlow<ByteArray?>(null)
    val receivedImage: StateFlow<ByteArray?> = _receivedImage

    private val _transferProgress = MutableStateFlow<String>("")
    val transferProgress: StateFlow<String> = _transferProgress

    private val _receivedCommand = MutableStateFlow<String?>(null)
    val receivedCommand: StateFlow<String?> = _receivedCommand

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo

    private var imageBuffer = mutableListOf<Byte>()
    private var expectedImageSize = 0
    private var isReceivingImage = false

    var isFullyInitialized = false
    private var notificationsEnabled = false
    private var mtuNegotiated = false
    private var currentMtuSize = 23

    private val deviceMap = mutableMapOf<String, android.bluetooth.BluetoothDevice>()

    private val handler = Handler(Looper.getMainLooper())

    private var lastDataReceivedTime = 0L
    private var currentChunkBuffer = mutableListOf<Byte>()

    // ==================== 日志和工具方法 ====================

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())
        val newLog = "[$timestamp] $message"
        _logs.value = (_logs.value + newLog).takeLast(100)
        Log.d(TAG, message)
    }

    // ==================== 扫描相关 ====================

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

    // ==================== 连接相关 ====================

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
        currentMtuSize = 23

        _deviceInfo.value = DeviceInfo(
            connectionState = "连接中...",
            deviceName = device.name ?: "未知",
            deviceAddress = device.address
        )

        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        addLog("🔌 断开连接")

        // 清空命令队列
        commandQueue.clear()
        isProcessingCommand = false

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = "未连接"
        _isConnected.value = false
        isFullyInitialized = false
        notificationsEnabled = false
        mtuNegotiated = false

        _deviceInfo.value = DeviceInfo()
    }

    @SuppressLint("MissingPermission")
    fun refreshDeviceInfo() {
        val gatt = bluetoothGatt ?: return

        val services = gatt.services ?: emptyList()
        var totalCharacteristics = 0
        var totalDescriptors = 0
        val characteristics = mutableListOf<CharacteristicInfo>()
        val cccdStates = mutableMapOf<String, Boolean>()

        services.forEach { service ->
            service.characteristics?.forEach { char ->
                totalCharacteristics++
                totalDescriptors += char.descriptors?.size ?: 0

                val properties = mutableListOf<String>()
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) properties.add("READ")
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) properties.add("WRITE")
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) properties.add("NOTIFY")
                if (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) properties.add("INDICATE")

                characteristics.add(
                    CharacteristicInfo(
                        uuid = char.uuid.toString(),
                        properties = properties
                    )
                )

                if (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                    char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
                    val descriptor = char.getDescriptor(BleConstants.CCCD_UUID)
                    val enabled = descriptor?.value?.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == true
                    cccdStates[char.uuid.toString()] = enabled
                }
            }
        }

        _deviceInfo.value = _deviceInfo.value.copy(
            serviceCount = services.size,
            characteristicCount = totalCharacteristics,
            descriptorCount = totalDescriptors,
            characteristics = characteristics,
            cccdStates = cccdStates
        )
    }

    // ==================== GATT 回调 ====================

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("✅ 已连接，协商MTU...")
                    _connectionState.value = "已连接"
                    _deviceInfo.value = _deviceInfo.value.copy(connectionState = "已连接")

                    handler.postDelayed({
                        gatt?.requestMtu(512)
                    }, 300)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addLog("❌ 连接断开")
                    _connectionState.value = "未连接"
                    _isConnected.value = false
                    _deviceInfo.value = _deviceInfo.value.copy(connectionState = "未连接")
                    isFullyInitialized = false
                    notificationsEnabled = false
                    mtuNegotiated = false

                    // 清空命令队列
                    commandQueue.clear()
                    isProcessingCommand = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                currentMtuSize = mtu
                addLog("✅ MTU协商成功: $mtu 字节 (可用载荷: ${mtu - 3} 字节)")
                _deviceInfo.value = _deviceInfo.value.copy(mtuSize = mtu)
                mtuNegotiated = true
                handler.postDelayed({
                    gatt?.discoverServices()
                }, 300)
            } else {
                addLog("⚠️ MTU协商失败，使用默认MTU 23字节")
                currentMtuSize = 23
                _deviceInfo.value = _deviceInfo.value.copy(mtuSize = 23)
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

                // Service 1 - 文件上传
                val service1 = gatt?.getService(BleConstants.SERVICE_1)
                fileDataCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_DATA)
                fileControlCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_CONTROL)
                fileNameCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_NAME)

                if (service1 != null) {
                    addLog("✅ 文件上传服务已找到")
                }

                // Service 2 - 图片传输
                val service2 = gatt?.getService(BleConstants.SERVICE_2)
                imageCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_LEN)
                commandCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_CMD)
                notificationCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_DATA)

                // Service 3 - 数据通知
                val service3 = gatt?.getService(BleConstants.SERVICE_3)
                statusNotificationCharacteristic = service3?.getCharacteristic(BleConstants.CHAR_DATA_NOTIFY)

                // 启用图片数据通知
                if (notificationCharacteristic != null) {
                    gatt?.setCharacteristicNotification(notificationCharacteristic, true)
                    val descriptor = notificationCharacteristic?.getDescriptor(BleConstants.CCCD_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                    addLog("🔔 启用图片数据通知 (0203)")
                } else {
                    addLog("⚠️ 图片数据通知特征不可用")
                }

                refreshDeviceInfo()
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
                    refreshDeviceInfo()
                }
            } else {
                addLog("⚠️ 描述符写入失败: $status")
            }
        }

        /**
         * 🆕 特征写入回调 - 处理所有写入操作的结果
         */
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("✅ 特征写入成功: ${characteristic?.uuid}")
                writeCallback?.onWriteSuccess()
            } else {
                addLog("❌ 特征写入失败: ${characteristic?.uuid}, status: $status")
                writeCallback?.onWriteFailure("GATT错误码: $status")
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

                        // 增加延迟，让ESP32有时间准备
                        handler.postDelayed({
                            requestImageData()
                        }, 150)
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

                            // 增加延迟，确保数据块完整接收
                            handler.removeCallbacks(chunkCompleteChecker)
                            handler.postDelayed(chunkCompleteChecker, 80)
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
                                    // 直接读取长度，不再发送 takeimage
                                    imageCharacteristic?.let {
                                        bluetoothGatt?.readCharacteristic(it)
                                        addLog("📖 读取图片长度...")
                                    }
                                }, 100)
                            }
                            "image_end" -> {
                                addLog("💾 传输完成信号")
                            }
                            "ai_work" -> {
                                addLog("🤖 收到AI处理命令")
                                _receivedCommand.value = "ai_work"

                                handler.postDelayed({
                                    readImageLength()
                                }, 200)
                            }
                            else -> {
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

    // ==================== 图片传输相关 ====================

    private val chunkCompleteChecker = Runnable {
        if (currentChunkBuffer.isNotEmpty()) {
            imageBuffer.addAll(currentChunkBuffer)
            val chunkSize = currentChunkBuffer.size
            currentChunkBuffer.clear()

            val progress = (imageBuffer.size * 100 / expectedImageSize)
            _transferProgress.value = "接收中 $progress% (${imageBuffer.size}/$expectedImageSize)"

            // 每隔5%才打印日志，减少日志开销
            if (progress % 5 == 0 || imageBuffer.size >= expectedImageSize) {
                addLog("接收进度: $progress% (${imageBuffer.size}/$expectedImageSize)")
            }

            if (imageBuffer.size >= expectedImageSize) {
                addLog("✅ 图片接收完成")
                _receivedImage.value = imageBuffer.toByteArray()
                isReceivingImage = false
                _transferProgress.value = ""
            } else {
                // 增加延迟，给ESP32更多时间准备数据
                handler.postDelayed({
                    requestImageData()
                }, 100)
            }
        }
    }

    // 命令队列，避免连续发送导致冲突
    private val commandQueue = mutableListOf<String>()
    private var isProcessingCommand = false

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化，请等待...")
            return
        }

        // 将命令加入队列
        commandQueue.add(command)

        // 如果没有正在处理的命令，开始处理
        if (!isProcessingCommand) {
            processNextCommand()
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextCommand() {
        if (commandQueue.isEmpty()) {
            isProcessingCommand = false
            return
        }

        isProcessingCommand = true
        val command = commandQueue.removeAt(0)

        commandCharacteristic?.let { char ->
            char.value = command.toByteArray()

            // 设置临时回调处理命令发送
            val originalCallback = writeCallback
            setWriteCallback(object : WriteCallback {
                override fun onWriteSuccess() {
                    addLog("✅ 命令发送成功: $command")
                    // 恢复原回调
                    setWriteCallback(originalCallback)
                    // 延迟后处理下一个命令
                    handler.postDelayed({
                        processNextCommand()
                    }, 50)
                }

                override fun onWriteFailure(error: String) {
                    addLog("❌ 命令发送失败: $command - $error")
                    // 恢复原回调
                    setWriteCallback(originalCallback)
                    // 失败后也要继续处理队列
                    handler.postDelayed({
                        processNextCommand()
                    }, 100)
                }
            })

            val result = bluetoothGatt?.writeCharacteristic(char)
            if (!result!!) {
                addLog("⚠️ 命令写入请求失败: $command")
                setWriteCallback(originalCallback)
                isProcessingCommand = false
            } else {
                addLog("📤 发送命令: $command")
            }
        } ?: run {
            addLog("⚠️ 命令特征不可用")
            isProcessingCommand = false
        }
    }

    @SuppressLint("MissingPermission")
    fun readImageLength() {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化，请等待...")
            return
        }

        // 先发送 takeimage 命令
        sendCommand("takeimage")

        // 等待命令处理完成后再读取长度
        handler.postDelayed({
            imageCharacteristic?.let {
                bluetoothGatt?.readCharacteristic(it)
                addLog("📖 读取图片长度...")
            }
        }, 200)  // 增加延迟，确保 ESP32 处理完 takeimage
    }

    @SuppressLint("MissingPermission")
    private fun requestImageData() {
        sendCommand("getimage")
    }

    // ==================== 文件上传相关 ====================

    /**
     * 设置写入回调
     */
    fun setWriteCallback(callback: WriteCallback?) {
        writeCallback = callback
    }

    /**
     * 发送文件数据
     * 写入到 Service 1 的数据特征 (0x0101)
     */
    @SuppressLint("MissingPermission")
    fun sendFileData(data: ByteArray): Boolean {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化")
            return false
        }

        fileDataCharacteristic?.let { char ->
            char.value = data
            val result = bluetoothGatt?.writeCharacteristic(char) ?: false
            if (result) {
                addLog("📤 发送数据: ${data.size} 字节")
            }
            return result
        }
        return false
    }

    /**
     * 发送文件控制命令
     * 写入到 Service 1 的控制特征 (0x0102)
     * 命令: "start", "update", "end"
     */
    @SuppressLint("MissingPermission")
    fun sendFileControl(command: String): Boolean {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化")
            return false
        }

        fileControlCharacteristic?.let { char ->
            char.value = command.toByteArray()
            val result = bluetoothGatt?.writeCharacteristic(char) ?: false
            if (result) {
                addLog("📤 文件控制: $command")
            }
            return result
        }
        return false
    }

    /**
     * 发送文件名
     * 写入到 Service 1 的文件名特征 (0x0103)
     */
    @SuppressLint("MissingPermission")
    fun sendFileName(fileName: String): Boolean {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未完全初始化")
            return false
        }

        fileNameCharacteristic?.let { char ->
            char.value = fileName.toByteArray()
            val result = bluetoothGatt?.writeCharacteristic(char) ?: false
            if (result) {
                addLog("📤 文件名: $fileName")
            }
            return result
        }
        return false
    }

    // ==================== 状态检查 ====================

    fun isImageReadyForTransfer(): Boolean {
        return isFullyInitialized && commandCharacteristic != null
    }

    fun isFileUploadReady(): Boolean {
        return isFullyInitialized &&
                fileDataCharacteristic != null &&
                fileControlCharacteristic != null &&
                fileNameCharacteristic != null
    }

    // ==================== 工具方法 ====================

    private fun byteArrayToInt(bytes: ByteArray): Int {
        return if (bytes.size >= 4) {
            (bytes[0].toInt() and 0xFF) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[3].toInt() and 0xFF) shl 24)
        } else 0
    }

    fun clearReceivedCommand() {
        _receivedCommand.value = null
    }
}