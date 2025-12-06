package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    companion object {
        lateinit var bleManager: BleManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BleManager(this)
        requestPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR Glass") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, "设备") },
                    label = { Text("设备") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Home, "应用") },
                    label = { Text("应用") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DeviceScreen()
                1 -> AppScreen()
            }
        }
    }
}

@Composable
fun DeviceScreen() {
    val devices by MainActivity.bleManager.devices.collectAsState()
    val logs by MainActivity.bleManager.logs.collectAsState()
    var isScanning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = {
                if (isScanning) {
                    MainActivity.bleManager.stopScan()
                    isScanning = false
                } else {
                    MainActivity.bleManager.startScan()
                    isScanning = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "停止扫描" else "扫描设备")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("设备列表", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            MainActivity.bleManager.connect(device.address)
                            isScanning = false
                        }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        Text(device.address, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("日志", style = MaterialTheme.typography.titleMedium)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(logs) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val isConnected by MainActivity.bleManager.isConnected.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("应用列表", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isConnected) {
                        context.startActivity(Intent(context, AiProcessActivity::class.java))
                    }
                }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI处理", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (isConnected) "点击进入" else "请先连接设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}