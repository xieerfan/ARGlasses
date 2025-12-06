package com.example.myapplication

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AiProcessActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var waitingForImage = false

    private val requestImageRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !waitingForImage) {
                MainActivity.bleManager.requestImage()
            }
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AiProcessScreen(
                        onBack = { finish() }
                    )
                }
            }
        }

        startAutoRequest()
        setupBleCallbacks()
    }

    private fun setupBleCallbacks() {
        // 使用lifecycleScope监听日志变化
        lifecycleScope.launch {
            MainActivity.bleManager.logs.collect { logs ->
                logs.lastOrNull()?.let { msg ->
                    if (msg.contains("image_end")) {
                        waitingForImage = false
                    }
                    if (msg.contains("图片大小:")) {
                        waitingForImage = true
                        handler.postDelayed({
                            requestImageData()
                        }, 500)
                    }
                }
            }
        }
    }

    private fun startAutoRequest() {
        isRunning = true
        handler.post(requestImageRunnable)
    }

    private fun requestImageData() {
        val runnable = object : Runnable {
            override fun run() {
                if (waitingForImage) {
                    MainActivity.bleManager.getImageData()
                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(runnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(requestImageRunnable)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProcessScreen(onBack: () -> Unit) {
    val logs by MainActivity.bleManager.logs.collectAsState()
    val imageData by MainActivity.bleManager.imageData.collectAsState()

    val bitmap = remember(imageData) {
        imageData?.let { data ->
            BitmapFactory.decodeByteArray(data, 0, data.size)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI处理") },
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
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    "状态: 自动请求中...",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "接收的图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("等待图片...")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "日志",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(16.dp)
            ) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(logs.takeLast(20)) { log ->
                        Text(
                            log,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}