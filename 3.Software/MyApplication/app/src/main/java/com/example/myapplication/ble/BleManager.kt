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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class BleManager(private val context: Context) {
    private val TAG = "BleManager"

    data class BleDevice(val name: String, val address: String)

    // ==================== 核心成员变量 ====================
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private val deviceMap = mutableMapOf<String, android.bluetooth.BluetoothDevice>()
    private val handler = Handler(Looper.getMainLooper())

    // BLE 特征
    private var fileDataCharacteristic: BluetoothGattCharacteristic? = null
    private var fileControlCharacteristic: BluetoothGattCharacteristic? = null
    private var fileNameCharacteristic: BluetoothGattCharacteristic? = null
    private var imageLengthCharacteristic: BluetoothGattCharacteristic? = null
    private var imageCommandCharacteristic: BluetoothGattCharacteristic? = null
    private var imageDataCharacteristic: BluetoothGattCharacteristic? = null

    // ✅ 修复：分离两个不同的特征
    private var controlCommandCharacteristic: BluetoothGattCharacteristic? = null  // 特征3_2 - 控制命令（发送）
    private var statusNotificationCharacteristic: BluetoothGattCharacteristic? = null  // 特征3_3 - 状态通知（接收）

    // UI 状态
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: MutableStateFlow<List<String>> = _logs

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _receivedImage = MutableStateFlow<ByteArray?>(null)
    val receivedImage: StateFlow<ByteArray?> = _receivedImage

    private val _transferProgress = MutableStateFlow<String>("")
    val transferProgress: StateFlow<String> = _transferProgress

    // ✅ 新增：AI工作命令状态
    private val _aiWorkCommand = MutableStateFlow<Boolean>(false)
    val aiWorkCommand: StateFlow<Boolean> = _aiWorkCommand

    // 连接状态
    var isFullyInitialized = false
    private var notificationsEnabled = false
    private var mtuNegotiated = false
    private var currentMtuSize = 23

    // ✅ 图片接收状态
    private var imageBuffer = ByteArray(0)
    private var expectedImageSize = 0
    private var currentImageOffset = 0
    private var isReceivingImage = false

    // ✅ 防止重复发送 takeimage
    private var isWaitingForImageReady = false

    // ==================== 日志 ====================
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = "[$timestamp] $message"
        _logs.value = (_logs.value + newLog).takeLast(100)
        Log.d(TAG, message)
    }

    // ==================== 扫描 ====================
    @SuppressLint("MissingPermission")
    fun startScan() {
        addLog("📡 开始扫描BLE设备...")
        _devices.value = emptyList()
        deviceMap.clear()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
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
                    val bleDevice = BleDevice(it.device.name ?: "未知设备", it.device.address)
                    val currentDevices = _devices.value.toMutableList()
                    if (!currentDevices.any { d -> d.address == bleDevice.address }) {
                        currentDevices.add(bleDevice)
                        _devices.value = currentDevices
                        deviceMap[bleDevice.address] = it.device
                        addLog("✅ 发现设备: ${bleDevice.name}")
                    }
                }
            }
        }
    }

    // ==================== 连接 ====================
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        deviceMap[address]?.let { connect(it) } ?: addLog("⚠️ 未找到设备: $address")
    }

    @SuppressLint("MissingPermission")
    fun connect(device: android.bluetooth.BluetoothDevice) {
        addLog("🔗 正在连接 ${device.name}...")
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
        _isConnected.value = false
        isFullyInitialized = false
    }

    // ==================== GATT 回调 ====================
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("✅ 已连接，协商MTU...")
                    handler.postDelayed({ gatt?.requestMtu(512) }, 300)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addLog("❌ 连接断开")
                    _isConnected.value = false
                    isFullyInitialized = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            currentMtuSize = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            mtuNegotiated = true
            addLog("✅ MTU: $currentMtuSize 字节")
            handler.postDelayed({ gatt?.discoverServices() }, 300)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            addLog("🔍 发现服务，初始化特征...")

            // Service 1 - 文件接收
            val service1 = gatt?.getService(BleConstants.SERVICE_1)
            fileDataCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_DATA)
            fileControlCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_CONTROL)
            fileNameCharacteristic = service1?.getCharacteristic(BleConstants.CHAR_FILE_NAME)

            Log.d(TAG, "特征初始化状态:")
            Log.d(TAG, "  1_1 (FILE_DATA): ${fileDataCharacteristic != null}")
            Log.d(TAG, "  1_2 (FILE_CONTROL): ${fileControlCharacteristic != null}")
            Log.d(TAG, "  1_3 (FILE_NAME): ${fileNameCharacteristic != null}")

            // Service 2 - 照片发送
            val service2 = gatt?.getService(BleConstants.SERVICE_2)
            imageLengthCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_LEN)
            imageCommandCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_CMD)
            imageDataCharacteristic = service2?.getCharacteristic(BleConstants.CHAR_IMAGE_DATA)

            // Service 3 - 数据获取和控制
            val service3 = gatt?.getService(BleConstants.SERVICE_3)

            // ✅ 修复：正确初始化两个不同的特征
            controlCommandCharacteristic = service3?.getCharacteristic(BleConstants.CHAR_DATA_IN)  // 特征3_2 - 控制命令
            statusNotificationCharacteristic = service3?.getCharacteristic(BleConstants.CHAR_DATA_NOTIFY)  // 特征3_3 - 状态通知

            Log.d(TAG, "  3_2 (CONTROL_CMD): ${controlCommandCharacteristic != null}")
            Log.d(TAG, "  3_3 (STATUS_NOTIFY): ${statusNotificationCharacteristic != null}")

            // ✅ 启用特征2_3通知（图片数据）
            imageDataCharacteristic?.let {
                gatt?.setCharacteristicNotification(it, true)
                val descriptor = it.getDescriptor(BleConstants.CCCD_UUID)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt?.writeDescriptor(descriptor)
                addLog("🔔 启用特征2_3通知")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            // ✅ 启用特征3_3通知（状态消息）
            if (!notificationsEnabled) {
                statusNotificationCharacteristic?.let {
                    gatt?.setCharacteristicNotification(it, true)
                    val desc = it.getDescriptor(BleConstants.CCCD_UUID)
                    desc?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(desc)
                    addLog("🔔 启用特征3_3通知")
                }
                notificationsEnabled = true
            } else {
                isFullyInitialized = true
                _isConnected.value = true
                addLog("🎉 初始化完成！")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            characteristic?.value?.let { data ->
                // ✅ 读取特征2_1 - 图片长度
                if (characteristic.uuid == BleConstants.CHAR_IMAGE_LEN) {
                    expectedImageSize = byteArrayToInt(data)
                    addLog("📦 图片长度 = $expectedImageSize 字节")
                    _transferProgress.value = "准备接收 $expectedImageSize 字节"

                    if (expectedImageSize > 0) {
                        imageBuffer = ByteArray(expectedImageSize)
                        currentImageOffset = 0
                        isReceivingImage = true
                        addLog("✅ 缓冲区已初始化")

                        // ✅ 自动发送第一个 getimage
                        handler.postDelayed({ sendImageCommand("getimage") }, 100)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { data ->
                when (characteristic.uuid) {
                    // ✅ 特征2_3 - 图片数据块（自动推送）
                    BleConstants.CHAR_IMAGE_DATA -> {
                        if (isReceivingImage && currentImageOffset < expectedImageSize) {
                            val copySize = Math.min(data.size, expectedImageSize - currentImageOffset)
                            System.arraycopy(data, 0, imageBuffer, currentImageOffset, copySize)
                            currentImageOffset += copySize

                            val progress = (currentImageOffset * 100 / expectedImageSize)
                            _transferProgress.value = "接收中... $progress%"
                            addLog("📥 接收数据块: $copySize 字节, 进度: ${currentImageOffset}/${expectedImageSize}")

                            // ✅ 检查是否接收完成
                            if (currentImageOffset >= expectedImageSize) {
                                addLog("✅ 接收完成，停止发送 getimage")
                                onImageReceiveComplete()
                            } else {
                                // ✅ 自动发送下一个 getimage（确保还需要接收）
                                handler.postDelayed({
                                    if (isReceivingImage && currentImageOffset < expectedImageSize) {
                                        sendImageCommand("getimage")
                                    }
                                }, 80)
                            }
                        } else {

                        }
                    }

                    // ✅ 特征3_3 - 状态通知
                    BleConstants.CHAR_DATA_NOTIFY -> {
                        val message = String(data, Charsets.UTF_8).trim()
                        addLog("📢 收到通知: '$message'")

                        when (message) {
                            "image_ready" -> {
                                // ✅ 只有第一次收到 image_ready 时才读取长度
                                if (isWaitingForImageReady) {
                                    isWaitingForImageReady = false
                                    addLog("🎉 Step 2️⃣: ESP32已准备图片")
                                    handler.postDelayed({
                                        imageLengthCharacteristic?.let {
                                            addLog("📖 Step 3️⃣: 读取图片长度特征...")
                                            gatt?.readCharacteristic(it)
                                        }
                                    }, 200)
                                } else {
                                    addLog("⚠️ 忽略额外的 image_ready（已在接收中）")
                                }
                            }
                            "image_end" -> {
                                addLog("📢 ESP32 通知图片发送完成")
                                if (isReceivingImage) {
                                    onImageReceiveComplete()
                                } else {

                                }
                            }
                            "image_empty" -> {
                                addLog("⚠️ 没有图像数据")
                                isReceivingImage = false
                            }
                            "image_error" -> {
                                addLog("❌ ESP32错误")
                                isReceivingImage = false
                            }
                            // ✅ 新增：处理 ai_work 命令
                            "ai_work" -> {
                                addLog("🤖 收到 AI 工作命令，准备启动 AI 处理...")
                                _aiWorkCommand.value = true
                                // 重置为 false，准备下次触发
                                handler.postDelayed({
                                    _aiWorkCommand.value = false
                                }, 100)
                            }
                            else -> {
                                // 其他通知消息
                            }
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    // ==================== 图片处理 ====================
    private fun onImageReceiveComplete() {
        if (!isReceivingImage) return

        addLog("💾 图片接收完成！")

        // ✅ 验证JPEG
        val isValid = imageBuffer.size >= 4 &&
                imageBuffer[0] == 0xFF.toByte() &&
                imageBuffer[1] == 0xD8.toByte() &&
                imageBuffer[imageBuffer.size - 2] == 0xFF.toByte() &&
                imageBuffer[imageBuffer.size - 1] == 0xD9.toByte()

        addLog(if (isValid) "✅ JPEG格式正确" else "⚠️ JPEG格式可能有问题")

        _receivedImage.value = imageBuffer.copyOf()
        isReceivingImage = false
        _transferProgress.value = "✅ 完成！"
        addLog("🎉 可以显示图片了！")
    }

    /**
     * ✅ 用户点击按钮 - 启动图片接收
     * 这是Android端主动请求图片的入口
     */
    @SuppressLint("MissingPermission")
    fun readImageLength() {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未初始化")
            return
        }

        // ✅ 防止在接收过程中重复请求
        if (isReceivingImage) {
            addLog("⚠️ 正在接收图片中，请稍候...")
            return
        }

        addLog("📸 Step 1️⃣: 发送 takeimage 命令...")
        _transferProgress.value = "发送 takeimage 命令..."

        // ✅ 设置标志，表示正在等待 image_ready
        isWaitingForImageReady = true

        sendImageCommand("takeimage")
    }

    /**
     * ✅ 发送图片命令 - 只发送纯命令，不处理响应
     */
    @SuppressLint("MissingPermission")
    fun sendImageCommand(command: String) {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未初始化")
            return
        }

        imageCommandCharacteristic?.let { char ->
            char.value = command.toByteArray(Charsets.UTF_8)
            val result = bluetoothGatt?.writeCharacteristic(char)
            if (result == true) {
                Log.d(TAG, "📤 发送图片命令: $command")
            } else {
                addLog("⚠️ 图片命令发送失败: $command")
            }
        } ?: run {
            addLog("⚠️ 图片命令特征不可用")
        }
    }

    // ==================== JSON发送 ====================

    /**
     * ✅ 新增：发送JSON并显示（用于AI生成的JSON）
     *
     * 完整流程：
     * 1. 发送文件名 /an/xxx.json 到特征1_3
     * 2. 发送start到特征1_2
     * 3. 分块发送JSON内容到特征1_1
     * 4. 发送end到特征1_2
     * 5. 发送display_json命令到特征3_2
     */
    @SuppressLint("MissingPermission")
    fun sendJsonForDisplay(jsonContent: String) {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未初始化，无法发送JSON")
            return
        }

        if (!_isConnected.value) {
            addLog("❌ BLE未连接，无法发送JSON")
            return
        }

        if (bluetoothGatt == null) {
            addLog("❌ bluetoothGatt为null，无法发送JSON")
            return
        }

        // 在后台线程中执行，避免阻塞UI
        kotlin.concurrent.thread {
            try {
                Log.d(TAG, "📤 开始发送JSON到ESP32并显示...")
                addLog("📤 开始发送JSON到ESP32...")

                val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
                Log.d(TAG, "📋 JSON大小: ${jsonBytes.size} 字节")
                addLog("📋 JSON大小: ${jsonBytes.size} 字节")

                // ========== Step 1: 发送文件名 /an/xxx.json ==========
                val jsonFileName = "/an/result_${System.currentTimeMillis()}.json"
                Log.d(TAG, "Step 1️⃣: 发送JSON文件名: $jsonFileName")
                addLog("Step 1️⃣: 发送文件名 $jsonFileName")

                fileNameCharacteristic?.let { char ->
                    char.value = jsonFileName.toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(200)
                } ?: run {
                    addLog("⚠️ 文件名特征不可用")
                    return@thread
                }

                // ========== Step 2: 发送 start 命令 ==========
                Log.d(TAG, "Step 2️⃣: 发送 start 命令")
                addLog("Step 2️⃣: 发送 start 命令")

                fileControlCharacteristic?.let { char ->
                    char.value = "start".toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(200)
                } ?: run {
                    addLog("⚠️ 控制特征不可用")
                    return@thread
                }

                // ========== Step 3: 分块发送JSON数据 ==========
                Log.d(TAG, "Step 3️⃣: 分块发送JSON数据")
                addLog("Step 3️⃣: 分块发送JSON数据")

                val chunkSize = 400
                var sentBytes = 0
                var chunkCount = 0

                fileDataCharacteristic?.let { char ->
                    while (sentBytes < jsonBytes.size) {
                        if (!_isConnected.value) {
                            addLog("❌ BLE连接已断开")
                            return@thread
                        }

                        val currentChunkSize = Math.min(chunkSize, jsonBytes.size - sentBytes)
                        val chunk = jsonBytes.sliceArray(sentBytes until sentBytes + currentChunkSize)

                        char.value = chunk
                        val result = bluetoothGatt?.writeCharacteristic(char)

                        if (result == true) {
                            sentBytes += currentChunkSize
                            chunkCount++
                            Log.d(TAG, "📤 数据块 $chunkCount: $currentChunkSize 字节 (总计: $sentBytes / ${jsonBytes.size})")
                        } else {
                            addLog("⚠️ 数据块 $chunkCount 发送失败")
                            return@thread
                        }

                        Thread.sleep(80)
                    }
                } ?: run {
                    addLog("⚠️ 数据特征不可用")
                    return@thread
                }

                Log.d(TAG, "✅ 全部 $chunkCount 个数据块已发送")
                addLog("✅ 已发送 $chunkCount 个数据块")

                // ========== Step 4: 发送 end 命令 ==========
                Log.d(TAG, "Step 4️⃣: 发送 end 命令")
                addLog("Step 4️⃣: 发送 end 命令")

                Thread.sleep(200)

                fileControlCharacteristic?.let { char ->
                    char.value = "end".toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(300)
                }

                Log.d(TAG, "✅ end 命令已发送")

                // ========== Step 5: 发送 display_json 命令到特征3_2 ==========
                Log.d(TAG, "Step 5️⃣: 发送 display_json 命令")
                addLog("Step 5️⃣: 发送 display_json 命令")

                Thread.sleep(200)

                controlCommandCharacteristic?.let { char ->
                    char.value = "display_json".toByteArray(Charsets.UTF_8)
                    val result = bluetoothGatt?.writeCharacteristic(char)
                    if (result == true) {
                        Log.d(TAG, "✅ display_json 命令已发送")
                        addLog("✅ display_json 命令已发送")
                    } else {
                        Log.e(TAG, "❌ display_json 命令发送失败")
                        addLog("⚠️ display_json 命令发送失败")
                    }
                }

                Log.d(TAG, "🎉 JSON发送和显示完成！")
                addLog("🎉 JSON已发送并显示在设备上！")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送JSON异常: ${e.message}", e)
                addLog("❌ 发送JSON异常: ${e.message}")
            }
        }
    }

    /**
     * ✅ 新增：发送JSON结果到ESP32（保留以兼容）
     * 使用 Service 1 的文件传输特征来发送JSON
     */
    @SuppressLint("MissingPermission")
    fun sendJsonResult(jsonContent: String) {
        if (!isFullyInitialized) {
            addLog("⚠️ 设备未初始化，无法发送JSON")
            return
        }

        if (!_isConnected.value) {
            addLog("❌ BLE未连接，无法发送JSON")
            return
        }

        if (bluetoothGatt == null) {
            addLog("❌ bluetoothGatt为null，无法发送JSON")
            return
        }

        // 在后台线程中执行，避免阻塞UI
        kotlin.concurrent.thread {
            try {
                Log.d(TAG, "📤 开始发送JSON到ESP32...")
                addLog("📤 开始发送JSON到ESP32...")

                val jsonBytes = jsonContent.toByteArray(Charsets.UTF_8)
                Log.d(TAG, "📋 JSON大小: ${jsonBytes.size} 字节")
                addLog("📋 JSON大小: ${jsonBytes.size} 字节")

                // ========== Step 1: 发送文件名 ==========
                val jsonFileName = "result_${System.currentTimeMillis()}.json"
                Log.d(TAG, "Step 1️⃣: 发送JSON文件名: $jsonFileName")
                addLog("Step 1️⃣: 发送JSON文件名")

                fileNameCharacteristic?.let { char ->
                    char.value = jsonFileName.toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(200)
                } ?: run {
                    addLog("⚠️ 文件名特征不可用")
                    return@thread
                }

                // ========== Step 2: 发送 start 命令 ==========
                Log.d(TAG, "Step 2️⃣: 发送 start 命令")
                addLog("Step 2️⃣: 发送 start 命令")

                fileControlCharacteristic?.let { char ->
                    char.value = "start".toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(200)
                } ?: run {
                    addLog("⚠️ 控制特征不可用")
                    return@thread
                }

                // ========== Step 3: 分块发送JSON数据 ==========
                Log.d(TAG, "Step 3️⃣: 分块发送JSON数据")
                addLog("Step 3️⃣: 分块发送JSON数据")

                val chunkSize = 400  // BLE MTU通常是512，减去20字节的包头
                var sentBytes = 0
                var chunkCount = 0

                fileDataCharacteristic?.let { char ->
                    while (sentBytes < jsonBytes.size) {
                        if (!_isConnected.value) {
                            addLog("❌ BLE连接已断开")
                            return@thread
                        }

                        val currentChunkSize = Math.min(chunkSize, jsonBytes.size - sentBytes)
                        val chunk = jsonBytes.sliceArray(sentBytes until sentBytes + currentChunkSize)

                        char.value = chunk
                        val result = bluetoothGatt?.writeCharacteristic(char)

                        if (result == true) {
                            sentBytes += currentChunkSize
                            chunkCount++
                            Log.d(TAG, "📤 数据块 $chunkCount: $currentChunkSize 字节 (总计: $sentBytes / ${jsonBytes.size})")
                            addLog("📤 数据块 $chunkCount: $currentChunkSize 字节")
                        } else {
                            addLog("⚠️ 数据块 $chunkCount 发送失败")
                            return@thread
                        }

                        Thread.sleep(80)  // 等待一下，避免发送过快
                    }
                } ?: run {
                    addLog("⚠️ 数据特征不可用")
                    return@thread
                }

                Log.d(TAG, "✅ 全部 $chunkCount 个数据块已发送")
                addLog("✅ 全部 $chunkCount 个数据块已发送")

                // ========== Step 4: 发送 end 命令 ==========
                Log.d(TAG, "Step 4️⃣: 发送 end 命令")
                addLog("Step 4️⃣: 发送 end 命令")

                Thread.sleep(200)

                fileControlCharacteristic?.let { char ->
                    char.value = "end".toByteArray(Charsets.UTF_8)
                    bluetoothGatt?.writeCharacteristic(char)
                    Thread.sleep(300)
                }

                Log.d(TAG, "🎉 JSON发送完成！")
                addLog("🎉 JSON发送完成！")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 发送JSON异常: ${e.message}", e)
                addLog("❌ 发送JSON异常: ${e.message}")
            }
        }
    }

    // ==================== 文件发送 ====================

    /**
     * ✅ 修复：发送文件数据到特征1_1
     * 添加连接检查和详细日志
     */
    @SuppressLint("MissingPermission")
    fun sendFileData(data: ByteArray): Boolean {
        if (!_isConnected.value) {
            Log.e(TAG, "❌ BLE未连接，无法发送文件数据")
            return false
        }

        if (fileDataCharacteristic == null) {
            Log.e(TAG, "❌ 文件数据特征(1_1)未初始化")
            return false
        }

        return fileDataCharacteristic?.let {
            it.value = data
            val result = bluetoothGatt?.writeCharacteristic(it) == true
            if (result) {
                Log.d(TAG, "✅ 文件数据已写入特征1_1: ${data.size} 字节")
            } else {
                Log.e(TAG, "❌ 文件数据写入失败: ${data.size} 字节")
            }
            result
        } ?: false
    }

    /**
     * ✅ 发送文件控制命令到特征1_2
     */
    @SuppressLint("MissingPermission")
    fun sendFileControl(command: String): Boolean {
        if (!_isConnected.value) {
            Log.e(TAG, "❌ BLE未连接，无法发送控制命令: $command")
            return false
        }

        if (fileControlCharacteristic == null) {
            Log.e(TAG, "❌ 文件控制特征(1_2)未初始化")
            return false
        }

        return fileControlCharacteristic?.let {
            it.value = command.toByteArray(Charsets.UTF_8)
            val result = bluetoothGatt?.writeCharacteristic(it) == true
            if (result) {
                Log.d(TAG, "✅ 文件控制命令已写入特征1_2: $command")
            } else {
                Log.e(TAG, "❌ 文件控制命令写入失败: $command")
            }
            result
        } ?: false
    }

    /**
     * ✅ 发送文件名到特征1_3
     */
    @SuppressLint("MissingPermission")
    fun sendFileName(fileName: String): Boolean {
        if (!_isConnected.value) {
            Log.e(TAG, "❌ BLE未连接，无法发送文件名: $fileName")
            return false
        }

        if (fileNameCharacteristic == null) {
            Log.e(TAG, "❌ 文件名特征(1_3)未初始化")
            return false
        }

        return fileNameCharacteristic?.let {
            it.value = fileName.toByteArray(Charsets.UTF_8)
            val result = bluetoothGatt?.writeCharacteristic(it) == true
            if (result) {
                Log.d(TAG, "✅ 文件名已写入特征1_3: $fileName")
            } else {
                Log.e(TAG, "❌ 文件名写入失败: $fileName")
            }
            result
        } ?: false
    }

    /**
     * ✅ 修复：发送控制命令到特征3_2
     *
     * 关键改动：使用 controlCommandCharacteristic（特征3_2）
     * 而不是 statusNotificationCharacteristic（特征3_3）
     *
     * 特征3_2 - 接收控制命令（write）
     * 特征3_3 - 发送状态通知（notify）
     */
    @SuppressLint("MissingPermission")
    fun sendControlCommand(command: String): Boolean {
        if (!_isConnected.value) {
            Log.e(TAG, "❌ BLE未连接，无法发送控制命令: $command")
            return false
        }

        if (controlCommandCharacteristic == null) {
            Log.e(TAG, "❌ 控制命令特征(3_2)未初始化")
            return false
        }

        return controlCommandCharacteristic?.let {
            it.value = command.toByteArray(Charsets.UTF_8)
            val result = bluetoothGatt?.writeCharacteristic(it) == true
            if (result) {
                Log.d(TAG, "✅ 控制命令已写入特征3_2: $command")
            } else {
                Log.e(TAG, "❌ 控制命令写入失败: $command")
            }
            result
        } ?: false
    }

    // ==================== 工具 ====================
    private fun byteArrayToInt(bytes: ByteArray): Int {
        return if (bytes.size >= 4) {
            (bytes[0].toInt() and 0xFF) or
                    ((bytes[1].toInt() and 0xFF) shl 8) or
                    ((bytes[2].toInt() and 0xFF) shl 16) or
                    ((bytes[3].toInt() and 0xFF) shl 24)
        } else 0
    }
}