package com.example.myapplication

import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream

class BleManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _imageData = MutableStateFlow<ByteArray?>(null)
    val imageData: StateFlow<ByteArray?> = _imageData

    private val _transferProgress = MutableStateFlow("")
    val transferProgress: StateFlow<String> = _transferProgress

    private var imageBuffer = mutableListOf<ByteArray>()
    private var expectedImageSize = 0
    private var receivedSize = 0
    private var isImageReady = false

    data class BleDevice(val name: String, val address: String)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                val name = device.name ?: "Unknown"
                if (name.contains("AR_GLASS")) {
                    val bleDevice = BleDevice(name, device.address)
                    if (!_devices.value.contains(bleDevice)) {
                        _devices.value = _devices.value + bleDevice
                    }
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    addLog("✅ 已连接到设备")
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    addLog("❌ 设备已断开")
                    _isConnected.value = false
                    _transferProgress.value = ""
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("🔍 服务发现成功")
                enableNotifications()
                _isConnected.value = true
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
            if (char.uuid == BleConstants.CHAR_IMAGE_LEN) {
                expectedImageSize = char.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0) ?: 0
                receivedSize = 0
                imageBuffer.clear()
                addLog("📦 图片大小: ${expectedImageSize} 字节")
                _transferProgress.value = "准备接收 ${expectedImageSize} 字节"
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
            when (char.uuid) {
                BleConstants.CHAR_IMAGE_DATA -> {
                    val data = char.value
                    imageBuffer.add(data)
                    receivedSize += data.size
                    val progress = (receivedSize * 100 / expectedImageSize)
                    _transferProgress.value = "接收中 $progress% ($receivedSize/$expectedImageSize)"
                    addLog("📥 接收: ${data.size}B, 总计: $receivedSize/$expectedImageSize")
                }
                BleConstants.CHAR_DATA_NOTIFY -> {
                    val msg = String(char.value)
                    addLog("📨 收到通知: $msg")
                    when (msg) {
                        "image_end" -> {
                            saveImage()
                            isImageReady = false
                            _transferProgress.value = "✅ 传输完成"
                        }
                        "image_ready" -> {
                            addLog("🎉 图片已准备就绪")
                            isImageReady = true
                            _transferProgress.value = "图片就绪，开始读取"
                            // 自动读取图片长度
                            readImageLength()
                        }
                    }
                }
            }
        }
    }

    fun startScan() {
        _devices.value = emptyList()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        addLog("🔍 开始扫描...")
    }

    fun stopScan() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        addLog("⏹️ 停止扫描")
    }

    fun connect(address: String) {
        val device = bluetoothAdapter?.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(context, false, gattCallback)
        addLog("🔄 正在连接...")
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isConnected.value = false
    }

    private fun enableNotifications() {
        bluetoothGatt?.let { gatt ->
            // 启用图片数据通知
            gatt.getService(BleConstants.SERVICE_2)?.getCharacteristic(BleConstants.CHAR_IMAGE_DATA)?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                char.getDescriptor(BleConstants.CCCD_UUID)?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }

            // 启用状态通知
            gatt.getService(BleConstants.SERVICE_3)?.getCharacteristic(BleConstants.CHAR_DATA_NOTIFY)?.let { char ->
                gatt.setCharacteristicNotification(char, true)
                char.getDescriptor(BleConstants.CCCD_UUID)?.let { desc ->
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(desc)
                }
            }
        }
    }

    fun requestImage() {
        bluetoothGatt?.getService(BleConstants.SERVICE_2)?.getCharacteristic(BleConstants.CHAR_IMAGE_CMD)?.let { char ->
            char.value = "takeimage".toByteArray()
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    fun readImageLength() {
        bluetoothGatt?.getService(BleConstants.SERVICE_2)?.getCharacteristic(BleConstants.CHAR_IMAGE_LEN)?.let { char ->
            bluetoothGatt?.readCharacteristic(char)
        }
    }

    fun getImageData() {
        bluetoothGatt?.getService(BleConstants.SERVICE_2)?.getCharacteristic(BleConstants.CHAR_IMAGE_CMD)?.let { char ->
            char.value = "getimage".toByteArray()
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    fun isImageReadyForTransfer(): Boolean = isImageReady

    private fun saveImage() {
        val totalData = ByteArray(receivedSize)
        var offset = 0
        for (chunk in imageBuffer) {
            System.arraycopy(chunk, 0, totalData, offset, chunk.size)
            offset += chunk.size
        }

        val fileName = "ar_glass_${System.currentTimeMillis()}.jpg"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { it.write(totalData) }

        addLog("💾 图片已保存: $fileName")
        _imageData.value = totalData

        imageBuffer.clear()
        receivedSize = 0
    }

    private fun addLog(msg: String) {
        val newLogs = (_logs.value + msg).takeLast(100)
        _logs.value = newLogs
    }
}