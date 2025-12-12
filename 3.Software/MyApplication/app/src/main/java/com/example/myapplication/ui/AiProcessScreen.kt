// 位置: com/example/myapplication/ui/AiProcessScreen.kt
package com.example.myapplication.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.ImageProcessingManager
import com.example.myapplication.network.AnswerUploadManager
import kotlinx.coroutines.flow.StateFlow
import java.io.File

// ============ ✅ 简化UI - 无Tab栏，单页面实时刷新，支持答案删除 ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProcessScreenV6(
    activity: Context,
    processingManager: ImageProcessingManager,
    answerUploadManager: AnswerUploadManager,
    onBack: () -> Unit,
    imageEnhancer: Any,
    imageSplitter: Any,
    receivedImages: MutableList<File>,
    enhancedImages: MutableList<File>,
    splitImages: MutableList<Pair<String, List<File>>>,
    receiveProgress: MutableList<String>,
    jsonResults: MutableList<File>,
    onTakePicture: () -> Unit,
    onStartProcess: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteAnswer: (String, String) -> Unit,
    isBleConnected: StateFlow<Boolean>,
    isProcessing: StateFlow<Boolean>,
    receivedImagesCount: StateFlow<Int>,
    enhancedImagesCount: StateFlow<Int>,
    progressLogsCount: StateFlow<Int>,
    jsonResultsCount: StateFlow<Int>
) {
    val bleConnected by isBleConnected.collectAsState()
    val processing by isProcessing.collectAsState()
    var selectedSubject by remember { mutableStateOf("physics") }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var jsonPreviewContent by remember { mutableStateOf<String?>(null) }
    var selectedAnswerId by remember { mutableStateOf<Pair<String, String>?>(null) }  // ✅ 新增：选中的答案ID和文件名

    val receivedCount by receivedImagesCount.collectAsState()
    val enhancedCount by enhancedImagesCount.collectAsState()
    val logsCount by progressLogsCount.collectAsState()
    val jsonCount by jsonResultsCount.collectAsState()

    if (previewFile != null) {
        ImagePreviewDialog(previewFile!!) { previewFile = null }
    }

    if (jsonPreviewContent != null) {
        JsonPreviewDialog(jsonPreviewContent!!) { jsonPreviewContent = null }
    }

    // ✅ 新增：答案删除确认对话框
    if (selectedAnswerId != null) {
        AlertDialog(
            onDismissRequest = { selectedAnswerId = null },
            title = { Text("确认删除答案") },
            text = { Text("确定要删除 ${selectedAnswerId?.second} 吗？此操作将同时删除本地文件和数据库记录。") },
            confirmButton = {
                Button(
                    onClick = {
                        val (answerId, fileName) = selectedAnswerId!!
                        onDeleteAnswer(answerId, fileName)
                        selectedAnswerId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { selectedAnswerId = null }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI 图片处理", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("收$receivedCount | 增$enhancedCount | 结$jsonCount | 日$logsCount", fontSize = 10.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                actions = {
                    Box(
                        modifier = Modifier
                            .background(if (bleConnected) Color(0xFF2E7D32) else Color(0xFF616161), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(if (bleConnected) "已连接" else "未连接", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SubjectSelectionDropdown(
                    processingManager = processingManager,
                    selectedSubject = selectedSubject,
                    onSubjectSelected = { selectedSubject = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = onTakePicture,
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(8.dp, CircleShape),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "拍照", modifier = Modifier.size(48.dp), tint = Color.White)
                }
                Text("拍照", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))

                Spacer(modifier = Modifier.height(30.dp))

                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("已收集照片", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$receivedCount 张", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStartProcess(selectedSubject) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !processing && receivedCount > 0 && !bleConnected,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (bleConnected) Color(0xFF9E9E9E) else Color(0xFF4CAF50)
                    )
                ) {
                    if (processing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (processing) "处理中..." else "开始处理 - ${processingManager.getSubjectChinese(selectedSubject)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDeleteAll,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除所有图片", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                if (bleConnected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BLE已连接，请通过设备端发起处理", fontSize = 12.sp, color = Color(0xFF1976D2))
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // ✅ 新增：答案结果显示和删除
            if (jsonResults.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI分析结果 (${jsonResults.size}个)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(jsonResults) { jsonFile ->
                            AnswerCardWithDelete(
                                jsonFile = jsonFile,
                                onPreview = {
                                    try {
                                        jsonPreviewContent = jsonFile.readText()
                                    } catch (e: Exception) {
                                        jsonPreviewContent = "读取失败: ${e.message}"
                                    }
                                },
                                onDelete = {
                                    // 使用文件名作为answer_id的一部分
                                    val answerId = jsonFile.nameWithoutExtension
                                    selectedAnswerId = Pair(answerId, jsonFile.name)
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("实时进度 (${receiveProgress.size}条)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (receiveProgress.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无日志", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
                        items(receiveProgress.reversed()) { log ->
                            LogItemV5(log)
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (enhancedImages.isNotEmpty()) {
                    Text("✨ 增强后的图片 (${enhancedImages.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(enhancedImages) { file ->
                            ImageCardV5(file, onClick = { previewFile = file })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (splitImages.isNotEmpty()) {
                    Text("📑 分割后的图片 (${splitImages.size}组)", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                        items(splitImages) { (name, files) ->
                            SplitCardV5(name, files, onClick = { previewFile = it })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (enhancedImages.isEmpty() && splitImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无结果", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * ✅ 新增：答案卡片组件（带删除按钮）
 */
@Composable
fun AnswerCardWithDelete(
    jsonFile: File,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF1976D2))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(jsonFile.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("${jsonFile.length() / 1024}KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ✅ 新增：删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// JSON预览对话框
@Composable
fun JsonPreviewDialog(content: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("JSON结果", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Black)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    content,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}

@Composable
fun SubjectSelectionDropdown(
    processingManager: ImageProcessingManager,
    selectedSubject: String,
    onSubjectSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val subjects = processingManager.getSupportedSubjects()

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "选择科目: ${processingManager.getSubjectChinese(selectedSubject)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            subjects.forEach { subject ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (subject == selectedSubject) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(processingManager.getSubjectChinese(subject))
                        }
                    },
                    onClick = {
                        onSubjectSelected(subject)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun LogItemV5(log: String) {
    val (icon, color) = when {
        log.contains("❌") -> Icons.Default.Clear to Color(0xFFD32F2F)
        log.contains("✅") -> Icons.Default.CheckCircle to Color(0xFF388E3C)
        log.contains("⚠️") -> Icons.Default.Warning to Color(0xFFF57C00)
        log.contains("🤖") || log.contains("📚") -> Icons.Default.Android to Color(0xFF1976D2)
        else -> Icons.Default.Info to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = color)
            Text(log, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp), maxLines = 2)
        }
    }
}

@Composable
fun ImageCardV5(file: File, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(100.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(80.dp).background(Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("大小: ${file.length() / 1024}KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SplitCardV5(name: String, files: List<File>, onClick: (File) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                Text("${files.size} 张", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                files.take(3).forEach { file ->
                    ImageThumbnailV5(file, modifier = Modifier.weight(1f)) { onClick(file) }
                }
                if (files.size > 3) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).background(Color.Gray, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                        Text("+${files.size - 3}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageThumbnailV5(file: File, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .background(Color.Gray)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun ImagePreviewDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.95f)
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
            }

            Box(modifier = Modifier.fillMaxSize(0.9f).background(Color.DarkGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.95f),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}
