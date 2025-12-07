package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class LogViewerActivity : ComponentActivity() {

    private val logLines = mutableStateListOf<String>()
    private var logcatProcess: Process? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LogViewerScreen(
                        onBack = { finish() },
                        logLines = logLines,
                        onStartLogcat = { startLogcat() },
                        onStopLogcat = { stopLogcat() },
                        onClearLogcat = { logLines.clear() } // 🔧 添加清除回调
                    )
                }
            }
        }
    }

    private fun startLogcat() {
        stopLogcat()
        logLines.clear()

        try {
            Runtime.getRuntime().exec("logcat -c")
            Thread.sleep(100)

            logcatProcess = Runtime.getRuntime().exec(
                "logcat -v time BleManager:D AndroidRuntime:E *:S"
            )

            lifecycleScope.launch {
                val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isNotEmpty()) {
                            handler.post {
                                logLines.add(line)
                                if (logLines.size > 500) {
                                    logLines.removeAt(0)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logLines.add("Error: ${e.message}")
        }
    }

    private fun stopLogcat() {
        logcatProcess?.destroy()
        logcatProcess = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLogcat()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class) // 🔧 添加实验性注解
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    logLines: List<String>,
    onStartLogcat: () -> Unit,
    onStopLogcat: () -> Unit,
    onClearLogcat: () -> Unit // 🔧 添加清除参数
) {
    var selectedMode by remember { mutableStateOf(0) }
    var isLogcatRunning by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }

    val bleManager = MainActivity.bleManager
    val bleLogs by bleManager.logs.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(
        if (selectedMode == 0) bleLogs.size else logLines.size,
        autoScroll
    ) {
        if (autoScroll) {
            val size = if (selectedMode == 0) bleLogs.size else logLines.size
            if (size > 0) {
                delay(50)
                listState.animateScrollToItem(size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "实时日志",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { autoScroll = !autoScroll }
                    ) {
                        Icon(
                            if (autoScroll) Icons.Filled.VerticalAlignBottom else Icons.Filled.PauseCircle,
                            contentDescription = if (autoScroll) "自动滚动" else "暂停滚动",
                            tint = if (autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            if (selectedMode == 0) {
                                bleManager.logs.value = emptyList() // 🔧 使用 .value
                            } else {
                                onClearLogcat() // 🔧 使用回调
                            }
                        }
                    ) {
                        Icon(Icons.Filled.DeleteSweep, "清空日志")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "日志模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == 0,
                            onClick = {
                                selectedMode = 0
                                if (isLogcatRunning) {
                                    onStopLogcat()
                                    isLogcatRunning = false
                                }
                            },
                            label = { Text("BLE简略日志") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (selectedMode == 1 && isLogcatRunning)
                                        Color(0xFF4CAF50)
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                selectedMode == 0 -> "实时BLE日志 (${bleLogs.size}条)"
                                isLogcatRunning -> "Logcat运行中 (${logLines.size}条)"
                                else -> "Logcat已停止"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val displayLogs = if (selectedMode == 0) bleLogs else logLines

                    if (displayLogs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (selectedMode == 0) Icons.Filled.BluetoothDisabled else Icons.Filled.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                if (selectedMode == 0) "暂无BLE日志" else "暂无Logcat输出",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (selectedMode == 0) "连接设备后会显示BLE通信日志" else "Logcat会显示应用的详细调试信息",
                                color = Color.Gray.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            items(displayLogs) { log ->
                                LogLine(log, selectedMode == 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogLine(log: String, isLogcat: Boolean) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = log,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = when {
                log.contains("Error", ignoreCase = true) || log.contains("❌") -> Color(0xFFEF5350)
                log.contains("Warning", ignoreCase = true) || log.contains("⚠️") -> Color(0xFFFFA726)
                log.contains("Success", ignoreCase = true) || log.contains("✅") -> Color(0xFF66BB6A)
                log.contains("📦") || log.contains("📤") || log.contains("📖") -> Color(0xFF42A5F5)
                log.contains("🤖") -> Color(0xFFAB47BC)
                isLogcat && log.contains(" D ") -> Color(0xFF81C784)
                isLogcat && log.contains(" I ") -> Color(0xFF64B5F6)
                isLogcat && log.contains(" W ") -> Color(0xFFFFB74D)
                isLogcat && log.contains(" E ") -> Color(0xFFE57373)
                else -> Color(0xFFE0E0E0)
            },
            lineHeight = 16.sp
        )
    }
}