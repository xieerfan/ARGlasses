package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.myapplication.config.ConfigManager
import com.example.myapplication.ui.*

/**
 * ✅ 主Activity - 包含轮询服务启动和所有原有功能
 *
 * 关键改动：
 * 1. 启动CommandPollingService后台服务
 * 2. 保留所有原有的BLE初始化和UI逻辑
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"

        // ✅ 全局BleManager引用（供CommandPollingService使用）
        lateinit var bleManager: BleManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "✨ 应用启动")

        // ==================== 初始化 ====================

        // 1️⃣ 初始化配置管理器
        ConfigManager.initialize(this)
        Log.d(TAG, "✅ 配置管理器已初始化")

        // 2️⃣ 初始化BleManager
        bleManager = BleManager(this)
        Log.d(TAG, "✅ BleManager已初始化")

        // 3️⃣ 请求必要权限
        requestPermissions()

        // ==================== ✅ 启动轮询服务 ====================

        startCommandPollingService()

        // ==================== UI初始化 ====================

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        Log.d(TAG, "✅ UI已加载")
    }

    /**
     * ✅ 启动命令轮询后台服务
     *
     * 功能：
     * 1. 后台定期轮询服务器获取命令
     * 2. 接收小说/音乐显示和播放命令
     * 3. 前台服务，不会被轻易杀死
     */
    private fun startCommandPollingService() {
        try {
            Log.d(TAG, "🚀 启动命令轮询服务...")

            val intent = Intent(this, CommandPollingService::class.java)

            // Android 8.0+ 需要使用前台服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                Log.d(TAG, "✅ 前台服务已启动 (Android 8.0+)")
            } else {
                startService(intent)
                Log.d(TAG, "✅ 服务已启动 (Android 8.0以下)")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动轮询服务失败: ${e.message}", e)
        }
    }

    /**
     * 请求必要的权限
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET  // ✅ 网络权限
        )

        // Android 12+ 需要额外的蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)
        Log.d(TAG, "✅ 权限申请已发送")
    }

    override fun onResume() {
        super.onResume()
        // ✅ 清空日志（保持UI整洁）
        bleManager.logs.value = emptyList()
        Log.d(TAG, "▶️ Activity恢复")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ Activity暂停")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🗑️ Activity销毁")
        // ✅ 不要关闭轮询服务，让它继续后台运行
    }
}

// ==================== 主屏幕（带导航） ====================

/**
 * 主屏幕 - 包含底部导航栏和内容切换
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen() {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                // 标签页1：设备
                NavigationBarItem(
                    icon = {
                        Icon(
                            Icons.Default.Devices,
                            contentDescription = "设备",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            "设备",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // 标签页2：应用
                NavigationBarItem(
                    icon = {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = "应用",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            "应用",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                // 标签页3：设置
                NavigationBarItem(
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = {
                        Text(
                            "设置",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            // ✅ 使用AnimatedContent实现平滑的标签页切换
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() with
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "tab_animation"
            ) { tab ->
                when (tab) {
                    0 -> DeviceScreen()      // 设备屏幕
                    1 -> AppScreen()         // 应用屏幕
                    2 -> UserScreen()        // 用户/设置屏幕
                }
            }
        }
    }
}

// ==================== 应用主题 ====================

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}