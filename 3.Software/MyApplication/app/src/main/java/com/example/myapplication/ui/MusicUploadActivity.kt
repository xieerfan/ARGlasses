package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.example.myapplication.config.ConfigManager
import com.example.myapplication.data.UploadFileInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MusicUploadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicUploadScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicUploadScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bleManager = MainActivity.bleManager
    val isConnected by bleManager.isConnected.collectAsState()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var uploadedFiles by remember { mutableStateOf<List<UploadFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isDeletingFile by remember { mutableStateOf<String?>(null) }
    var uploadingFileName by remember { mutableStateOf<String?>(null) }
    var uploadStatus by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        fetchMusicListFromServer { files ->
            uploadedFiles = files
            isLoading = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToFileMusic(context, uri, "mp3")
            if (file != null) {
                selectedFile = file
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音乐上传", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (isConnected) "🔗 BLE已连接" else "⚠️ BLE未连接", fontWeight = FontWeight.Bold)
                    Icon(
                        if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = if (isConnected) Color.Green else Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (selectedFile == null) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("请选择MP3音乐文件")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            enabled = uploadingFileName == null
                        ) {
                            Text("选择音乐")
                        }
                    } else {
                        Text("已选择: ${selectedFile!!.name}", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("大小: ${formatFileSizeMusic(selectedFile!!.length())}", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                selectedFile?.let { file ->
                                    val fileName = "/sdcard/music/${file.name}"
                                    Log.d("MusicUploadActivity", "📤 开始上传: ${file.name}")

                                    uploadingFileName = file.name
                                    uploadStatus = "📤 BLE 上传中..."

                                    val fileData = file.readBytes()

                                    BleCommandSender.uploadFileData(fileData, fileName) {
                                        Log.d("MusicUploadActivity", "✅ BLE 上传完成")
                                        uploadStatus = "📤 服务器同步中..."

                                        uploadMusicToServer(context, file) { success ->
                                            if (success) {
                                                Log.d("MusicUploadActivity", "✅ 服务器同步成功")
                                                uploadStatus = "🔄 刷新列表中..."

                                                Thread.sleep(1000)
                                                fetchMusicListFromServer { files ->
                                                    uploadedFiles = files
                                                    uploadStatus = "✅ 上传完成！"
                                                    Log.d("MusicUploadActivity", "📋 列表已刷新，共 ${files.size} 首")

                                                    uploadingFileName = null
                                                    selectedFile = null

                                                    Thread.sleep(2000)
                                                    uploadStatus = ""
                                                }
                                            } else {
                                                Log.e("MusicUploadActivity", "❌ 服务器同步失败")
                                                uploadStatus = "⚠️ 设备已接收，但服务器同步失败"
                                                uploadingFileName = null
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uploadingFileName == null && isConnected
                        ) {
                            if (uploadingFileName != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("上传中...")
                            } else {
                                Text("上传到 ESP32 + 服务器")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { selectedFile = null },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uploadingFileName == null
                        ) {
                            Text("取消")
                        }
                    }

                    if (uploadStatus.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                uploadStatus.contains("BLE") -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                uploadStatus.contains("✅") -> {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color.Green)
                                }
                                uploadStatus.contains("⚠️") -> {
                                    Icon(Icons.Filled.Warning, null, tint = Color.Yellow)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uploadStatus, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("已上传的音乐 (${uploadedFiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("加载中...")
                            }
                        }
                    }
                    uploadedFiles.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("暂无已上传的音乐", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uploadedFiles) { fileInfo ->
                                UploadedMusicItemWithDelete(
                                    fileInfo,
                                    isDeleting = isDeletingFile == fileInfo.fileName,
                                    isUploading = uploadingFileName != null
                                ) {
                                    isDeletingFile = fileInfo.fileName
                                    deleteMusicFile(context, fileInfo.id) {
                                        isDeletingFile = null
                                        fetchMusicListFromServer { files ->
                                            uploadedFiles = files
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadedMusicItemWithDelete(
    fileInfo: UploadFileInfo,
    isDeleting: Boolean = false,
    isUploading: Boolean = false,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    tint = Color(0xFF1DB954)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileInfo.fileName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatFileSizeMusic(fileInfo.fileSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = formatTimeMusic(fileInfo.uploadTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
            if (isDeleting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (!isUploading) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.Red)
                }
            }
        }
    }
}

/**
 * ✅ 上传音乐信息到服务器（JSON格式）
 */
fun uploadMusicToServer(context: android.content.Context, file: File, onComplete: (Boolean) -> Unit) {
    Thread {
        try {
            val config = ConfigManager.getConfig()
            val serverIp = config.server.ip
            val serverPort = config.server.port

            if (serverIp.isEmpty() || serverPort.isEmpty()) {
                Log.w("MusicUploadActivity", "⚠️ 服务器配置未设置")
                onComplete(false)
                return@Thread
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val url = "http://${serverIp}:${serverPort}/api/upload/music"
            Log.d("MusicUploadActivity", "📡 上传文件信息到服务器: $url")

            // ✅ 根据服务端API，发送JSON格式
            val requestJson = JSONObject().apply {
                put("music_name", file.name)
                put("file_size", file.length() / (1024.0 * 1024))  // 转换为MB
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            Log.d("MusicUploadActivity", "📤 请求体: $requestJson")

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Log.d("MusicUploadActivity", "✅ 服务器响应: $body")

                try {
                    val json = JSONObject(body)
                    val success = json.optBoolean("success", false)
                    if (success) {
                        Log.d("MusicUploadActivity", "✅ 音乐信息上传成功")
                        onComplete(true)
                    } else {
                        Log.e("MusicUploadActivity", "❌ 服务器返回失败: ${json.optString("message")}")
                        onComplete(false)
                    }
                } catch (e: Exception) {
                    Log.e("MusicUploadActivity", "❌ JSON 解析失败: ${e.message}")
                    onComplete(false)
                }
            } else {
                Log.e("MusicUploadActivity", "❌ 服务器错误: ${response.code}")
                Log.e("MusicUploadActivity", "❌ 响应体: ${response.body?.string()}")
                onComplete(false)
            }

        } catch (e: Exception) {
            Log.e("MusicUploadActivity", "❌ 上传异常: ${e.message}", e)
            onComplete(false)
        }
    }.start()
}

fun fetchMusicListFromServer(onSuccess: (List<UploadFileInfo>) -> Unit) {
    Thread {
        try {
            val config = ConfigManager.getConfig()
            val serverIp = config.server.ip
            val serverPort = config.server.port

            if (serverIp.isEmpty() || serverPort.isEmpty()) {
                Log.w("MusicUploadActivity", "⚠️ 服务器配置未设置")
                onSuccess(emptyList())
                return@Thread
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val url = "http://${serverIp}:${serverPort}/api/music"
            Log.d("MusicUploadActivity", "📡 获取音乐列表: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val files = parseMusicResponse(body)
                Log.d("MusicUploadActivity", "✅ 解析成功，共 ${files.size} 首音乐")
                onSuccess(files)
            } else {
                Log.e("MusicUploadActivity", "❌ API 失败: ${response.code}")
                onSuccess(emptyList())
            }

        } catch (e: Exception) {
            Log.e("MusicUploadActivity", "❌ 异常: ${e.message}", e)
            onSuccess(emptyList())
        }
    }.start()
}

private fun parseMusicResponse(jsonString: String): List<UploadFileInfo> {
    return try {
        val root = JSONObject(jsonString)

        if (!root.optBoolean("success")) {
            return emptyList()
        }

        val data = root.optJSONObject("data") ?: return emptyList()
        val listArray = data.optJSONArray("list") ?: return emptyList()

        val result = mutableListOf<UploadFileInfo>()

        for (i in 0 until listArray.length()) {
            val item = listArray.getJSONObject(i)

            val musicId = item.optInt("id", 0)
            val musicName = item.optString("music_name", "未知")
            val fileSizeMb = item.optDouble("file_size_mb", 0.0)
            val uploadTimeStr = item.optString("upload_time", "")

            val fileSize = (fileSizeMb * 1024 * 1024).toLong()
            val uploadTime = parseTimeStringMusic(uploadTimeStr)

            result.add(
                UploadFileInfo(
                    id = musicId,
                    fileName = musicName,
                    fileSize = fileSize,
                    filePath = "/music/$musicName",
                    uploadTime = uploadTime
                )
            )
        }

        result
    } catch (e: Exception) {
        Log.e("MusicUploadActivity", "❌ JSON 解析异常: ${e.message}", e)
        emptyList()
    }
}

private fun parseTimeStringMusic(timeStr: String): Long {
    return try {
        if (timeStr.isEmpty()) System.currentTimeMillis()
        else {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.parse(timeStr)?.time ?: System.currentTimeMillis()
        }
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

fun deleteMusicFile(context: android.content.Context, musicId: Int, onComplete: () -> Unit) {
    Thread {
        try {
            val config = ConfigManager.getConfig()
            val serverIp = config.server.ip
            val serverPort = config.server.port

            Log.d("MusicUploadActivity", "🗑️ 删除音乐: ID=$musicId")

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val url = "http://${serverIp}:${serverPort}/api/music/$musicId"

            val request = Request.Builder().url(url).delete().build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                Log.d("MusicUploadActivity", "✅ 删除成功: ID=$musicId")
            } else {
                Log.e("MusicUploadActivity", "❌ 删除失败: HTTP ${response.code}")
            }

            onComplete()
        } catch (e: Exception) {
            Log.e("MusicUploadActivity", "❌ 删除异常: ${e.message}", e)
            onComplete()
        }
    }.start()
}

fun copyUriToFileMusic(context: android.content.Context, uri: Uri, extension: String): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "music_${System.currentTimeMillis()}.$extension"
        val outputFile = File(context.filesDir, fileName)
        inputStream.use { input -> outputFile.outputStream().use { output -> input.copyTo(output) } }
        outputFile
    } catch (e: Exception) {
        Log.e("MusicUploadActivity", "❌ 文件复制失败: ${e.message}", e)
        null
    }
}

fun formatFileSizeMusic(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${String.format("%.2f", bytes / (1024.0 * 1024))} MB"
}

fun formatTimeMusic(timeMillis: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timeMillis))
}