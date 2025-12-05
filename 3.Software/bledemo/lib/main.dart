import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:path_provider/path_provider.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ESP32 图片接收',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const BLEReceiverPage(),
    );
  }
}

class BLEReceiverPage extends StatefulWidget {
  const BLEReceiverPage({Key? key}) : super(key: key);

  @override
  State<BLEReceiverPage> createState() => _BLEReceiverPageState();
}

class _BLEReceiverPageState extends State<BLEReceiverPage> {
  // BLE 相关
  FlutterBluePlus flutterBlue = FlutterBluePlus();
  BluetoothDevice? connectedDevice;
  BluetoothCharacteristic? txCharacteristic;
  BluetoothCharacteristic? rxCharacteristic;

  // UUID (与 ESP32 对应)
  static const String serviceUuid = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
  static const String txCharUuid = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
  static const String rxCharUuid = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

  // 状态
  bool isScanning = false;
  bool isConnected = false;
  String statusMessage = "未连接";

  // 图片接收缓冲
  bool receivingImage = false;
  String? currentFileName;
  int? expectedFileSize;
  List<int> imageBuffer = [];
  double receiveProgress = 0.0;

  // 接收到的图片列表
  List<String> receivedImages = [];

  // 日志
  List<String> logs = [];

  @override
  void initState() {
    super.initState();
    _requestPermissions();
  }

  @override
  void dispose() {
    connectedDevice?.disconnect();
    super.dispose();
  }

  // 请求权限
  Future<void> _requestPermissions() async {
    if (Platform.isAndroid) {
      await [
        Permission.bluetooth,
        Permission.bluetoothScan,
        Permission.bluetoothConnect,
        Permission.location,
        Permission.storage,
      ].request();
    }
  }

  // 添加日志
  void _addLog(String message) {
    setState(() {
      logs.insert(0, "[${DateTime.now().toString().substring(11, 19)}] $message");
      if (logs.length > 50) logs.removeLast();
    });
    print(message);
  }

  // 扫描设备
  Future<void> _scanForDevices() async {
    setState(() {
      isScanning = true;
      statusMessage = "扫描中...";
    });
    _addLog("开始扫描 BLE 设备...");

    try {
      // 开始扫描
      FlutterBluePlus.startScan(timeout: const Duration(seconds: 10));

      // 监听扫描结果
      FlutterBluePlus.scanResults.listen((results) {
        for (ScanResult result in results) {
          if (result.device.platformName == "ESP32_ImageSender") {
            _addLog("发现设备: ${result.device.platformName}");
            FlutterBluePlus.stopScan();
            _connectToDevice(result.device);
            break;
          }
        }
      });

      // 等待扫描完成
      await Future.delayed(const Duration(seconds: 10));
      FlutterBluePlus.stopScan();

      if (!isConnected) {
        setState(() {
          statusMessage = "未找到设备";
          isScanning = false;
        });
        _addLog("扫描完成，未找到 ESP32 设备");
      }
    } catch (e) {
      _addLog("扫描错误: $e");
      setState(() {
        isScanning = false;
        statusMessage = "扫描失败";
      });
    }
  }

  // 连接设备
  Future<void> _connectToDevice(BluetoothDevice device) async {
    try {
      _addLog("正在连接到 ${device.platformName}...");
      await device.connect();

      setState(() {
        connectedDevice = device;
        isConnected = true;
        isScanning = false;
        statusMessage = "已连接";
      });
      _addLog("连接成功");

      // 发现服务
      List<BluetoothService> services = await device.discoverServices();

      for (BluetoothService service in services) {
        if (service.uuid.toString().toLowerCase() == serviceUuid.toLowerCase()) {
          _addLog("找到服务: ${service.uuid}");

          for (BluetoothCharacteristic char in service.characteristics) {
            if (char.uuid.toString().toLowerCase() == txCharUuid.toLowerCase()) {
              txCharacteristic = char;
              _addLog("找到 TX 特征");

              // 订阅通知
              await char.setNotifyValue(true);
              char.lastValueStream.listen(_onDataReceived);
              _addLog("已订阅数据通知");
            }

            if (char.uuid.toString().toLowerCase() == rxCharUuid.toLowerCase()) {
              rxCharacteristic = char;
              _addLog("找到 RX 特征");
            }
          }
        }
      }
    } catch (e) {
      _addLog("连接错误: $e");
      setState(() {
        isConnected = false;
        statusMessage = "连接失败";
      });
    }
  }

  // 接收数据回调
  void _onDataReceived(List<int> data) {
    if (data.isEmpty) return;

    String dataStr = String.fromCharCodes(data);

    // 检查是否是消息头
    if (dataStr.startsWith("IMG|")) {
      _handleImageHeader(dataStr);
    } else if (dataStr == "IMG_END") {
      _handleImageEnd();
    } else if (dataStr.startsWith("CMD|")) {
      _handleCommand(dataStr);
    } else if (receivingImage) {
      // 接收图片数据
      imageBuffer.addAll(data);
      setState(() {
        receiveProgress = imageBuffer.length / (expectedFileSize ?? 1);
      });
    }
  }

  // 处理图片头
  void _handleImageHeader(String header) {
    List<String> parts = header.split("|");
    if (parts.length >= 3) {
      currentFileName = parts[1].split("/").last;
      expectedFileSize = int.tryParse(parts[2]);

      setState(() {
        receivingImage = true;
        imageBuffer.clear();
        receiveProgress = 0.0;
      });

      _addLog("开始接收图片: $currentFileName ($expectedFileSize 字节)");
    }
  }

  // 处理图片结束
  Future<void> _handleImageEnd() async {
    if (!receivingImage || imageBuffer.isEmpty) return;

    try {
      // 保存图片到本地
      Directory appDir = await getApplicationDocumentsDirectory();
      String imagePath = "${appDir.path}/$currentFileName";

      File imageFile = File(imagePath);
      await imageFile.writeAsBytes(imageBuffer);

      setState(() {
        receivingImage = false;
        receivedImages.insert(0, imagePath);
        receiveProgress = 0.0;
      });

      _addLog("图片接收完成: $currentFileName (${imageBuffer.length} 字节)");
      _addLog("保存路径: $imagePath");

      // 清空缓冲
      imageBuffer.clear();
    } catch (e) {
      _addLog("保存图片失败: $e");
    }
  }

  // 处理命令
  void _handleCommand(String cmdStr) {
    List<String> parts = cmdStr.split("|");
    if (parts.length >= 2) {
      String cmd = parts[1];
      _addLog("收到命令: $cmd");
      print("sw");  // 按照要求 print
    }
  }

  // 断开连接
  Future<void> _disconnect() async {
    await connectedDevice?.disconnect();
    setState(() {
      connectedDevice = null;
      isConnected = false;
      statusMessage = "已断开";
      receivingImage = false;
    });
    _addLog("已断开连接");
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ESP32 图片接收'),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 状态栏
          Container(
            padding: const EdgeInsets.all(16),
            color: isConnected ? Colors.green.shade100 : Colors.grey.shade200,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      isConnected ? Icons.bluetooth_connected : Icons.bluetooth_disabled,
                      color: isConnected ? Colors.green : Colors.grey,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      statusMessage,
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: isConnected ? Colors.green.shade900 : Colors.grey.shade700,
                      ),
                    ),
                  ],
                ),
                if (isConnected)
                  ElevatedButton(
                    onPressed: _disconnect,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.red,
                      foregroundColor: Colors.white,
                    ),
                    child: const Text('断开'),
                  )
                else
                  ElevatedButton(
                    onPressed: isScanning ? null : _scanForDevices,
                    child: Text(isScanning ? '扫描中...' : '扫描连接'),
                  ),
              ],
            ),
          ),

          // 接收进度
          if (receivingImage)
            Container(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text('正在接收: $currentFileName'),
                  const SizedBox(height: 8),
                  LinearProgressIndicator(value: receiveProgress),
                  Text('${(receiveProgress * 100).toStringAsFixed(1)}%'),
                ],
              ),
            ),

          // 标签页
          Expanded(
            child: DefaultTabController(
              length: 2,
              child: Column(
                children: [
                  const TabBar(
                    tabs: [
                      Tab(icon: Icon(Icons.image), text: '图片'),
                      Tab(icon: Icon(Icons.notes), text: '日志'),
                    ],
                  ),
                  Expanded(
                    child: TabBarView(
                      children: [
                        // 图片列表
                        _buildImageList(),
                        // 日志列表
                        _buildLogList(),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildImageList() {
    if (receivedImages.isEmpty) {
      return const Center(
        child: Text('暂无接收的图片', style: TextStyle(fontSize: 16)),
      );
    }

    return ListView.builder(
      itemCount: receivedImages.length,
      itemBuilder: (context, index) {
        String imagePath = receivedImages[index];
        return Card(
          margin: const EdgeInsets.all(8),
          child: ListTile(
            leading: Image.file(
              File(imagePath),
              width: 60,
              height: 60,
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) {
                return const Icon(Icons.broken_image, size: 60);
              },
            ),
            title: Text(imagePath.split("/").last),
            subtitle: Text('路径: $imagePath'),
            onTap: () {
              // 点击查看大图
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => ImageViewPage(imagePath: imagePath),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildLogList() {
    if (logs.isEmpty) {
      return const Center(
        child: Text('暂无日志', style: TextStyle(fontSize: 16)),
      );
    }

    return ListView.builder(
      itemCount: logs.length,
      itemBuilder: (context, index) {
        return ListTile(
          dense: true,
          title: Text(
            logs[index],
            style: const TextStyle(fontSize: 12, fontFamily: 'monospace'),
          ),
        );
      },
    );
  }
}

// 图片查看页面
class ImageViewPage extends StatelessWidget {
  final String imagePath;

  const ImageViewPage({Key? key, required this.imagePath}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(imagePath.split("/").last),
      ),
      body: Center(
        child: InteractiveViewer(
          child: Image.file(File(imagePath)),
        ),
      ),
    );
  }
}