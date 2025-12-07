// NovelUploadActivity.kt（修正版）
package com.example.myapplication

import android.os.Bundle
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
import java.io.File

class NovelUploadActivity : ComponentActivity() {

    private lateinit var uploadManager: FileUploadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uploadManager = FileUploadManager(MainActivity.bleManager)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NovelUploadScreen(
                        uploadManager = uploadManager,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelUploadScreen(
    uploadManager: FileUploadManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val bleManager = MainActivity.bleManager
    val isConnected by bleManager.isConnected.collectAsState()
    val uploadProgress by uploadManager.uploadProgress.collectAsState()

    // 添加网络上传状态监听
    val networkUploadState by NetworkManager.uploadState.collectAsState()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var uploadedFiles by remember { mutableStateOf<List<UploadFileInfo>>(emptyList()) }

    // 监听上传完成
    LaunchedEffect(uploadProgress) {
        if (uploadProgress?.isComplete == true && uploadProgress?.errorMessage == null) {
            selectedFile?.let { file ->
                uploadedFiles = uploadedFiles + UploadFileInfo(
                    fileName = file.name,
                    fileSize = file.length(),
                    filePath = "/novel/${file.name}"
                )
            }
            selectedFile = null
        }
    }

    // 监听网络上传状态
    LaunchedEffect(networkUploadState) {
        networkUploadState?.let { state ->
            when (state) {
                is NetworkResult.Error -> {
                    // 网络上传失败，已在 FileUploadManager 中处理
                }
                is NetworkResult.Success -> {
                    // 网络上传成功，已在 FileUploadManager 中处理
                }
                is NetworkResult.Loading -> {
                    // 正在上传到服务器
                }
            }
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val file = copyUriToFile(context, uri, "txt")
            if (file != null) {
                selectedFile = file
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "小说上传",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            // 连接状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (isConnected) "设备已连接" else "设备未连接，请先连接设备",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选择文件按钮
            Button(
                onClick = { filePickerLauncher.launch("text/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && uploadProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Filled.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "选择TXT文件",
                    fontWeight = FontWeight.Bold
                )
            }

            // 显示选中的文件
            selectedFile?.let { file ->
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "已选择文件",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "文件名: ${file.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "大小: ${formatFileSize(file.length())}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 上传按钮
                Button(
                    onClick = {
                        uploadManager.uploadFile(file, FileType.NOVEL)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isConnected && uploadProgress == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        "上传到设备",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 上传进度
            uploadProgress?.let { progress ->
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            progress.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                            progress.isComplete -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 标题
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    progress.errorMessage != null -> Icons.Filled.Error
                                    progress.isComplete -> Icons.Filled.CheckCircle
                                    else -> Icons.Filled.Upload
                                },
                                contentDescription = null,
                                tint = when {
                                    progress.errorMessage != null -> MaterialTheme.colorScheme.error
                                    progress.isComplete -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                when {
                                    progress.errorMessage != null -> "上传失败"
                                    progress.isComplete && progress.message?.contains("服务器") == true -> "同步完成"
                                    progress.isComplete -> "上传完成"
                                    else -> "上传中..."
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 文件名
                        Text(
                            progress.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (progress.errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                progress.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (progress.message != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                progress.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))

                            // 进度条
                            LinearProgressIndicator(
                                progress = progress.progress / 100f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 进度信息
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${progress.progress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )

                                Text(
                                    "${formatFileSize(progress.uploadedSize)}/${formatFileSize(progress.totalSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        // 显示网络上传状态
                        networkUploadState?.let { state ->
                            when (state) {
                                is NetworkResult.Loading -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "正在同步到服务器...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                is NetworkResult.Error -> {
                                    // 错误已在主进度中显示
                                }
                                is NetworkResult.Success -> {
                                    // 成功已在主进度中显示
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 已上传文件列表
            Text(
                "已上传的小说 (${uploadedFiles.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (uploadedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "暂无已上传的小说",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uploadedFiles) { fileInfo ->
                            UploadedFileItem(fileInfo, FileType.NOVEL)
                        }
                    }
                }
            }
        }
    }
}