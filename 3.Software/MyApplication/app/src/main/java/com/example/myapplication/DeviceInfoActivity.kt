package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DeviceInfoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DeviceInfoScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    val bleManager = MainActivity.bleManager
    val isConnected by bleManager.isConnected.collectAsState()
    val deviceInfo by bleManager.deviceInfo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "设备信息",
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
                        onClick = {
                            if (isConnected) {
                                bleManager.refreshDeviceInfo()
                            }
                        },
                        enabled = isConnected
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "刷新",
                            tint = if (isConnected)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
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
        if (!isConnected) {
            // 未连接状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "未连接设备",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "请先在设备页面连接BLE设备",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // 已连接状态，显示设备信息
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 连接状态卡片
                item {
                    InfoCard(
                        title = "连接状态",
                        icon = Icons.Filled.BluetoothConnected,
                        iconColor = Color(0xFF4CAF50)
                    ) {
                        InfoRow("状态", deviceInfo.connectionState, Color(0xFF4CAF50))
                        InfoRow("设备名称", deviceInfo.deviceName)
                        InfoRow("设备地址", deviceInfo.deviceAddress)
                    }
                }

                // MTU信息卡片
                item {
                    InfoCard(
                        title = "MTU配置",
                        icon = Icons.Filled.Speed,
                        iconColor = Color(0xFF2196F3)
                    ) {
                        InfoRow("当前MTU", "${deviceInfo.mtuSize} 字节")
                        InfoRow("可用载荷", "${deviceInfo.mtuSize - 3} 字节")
                        InfoRow(
                            "传输效率",
                            when {
                                deviceInfo.mtuSize >= 512 -> "优秀 ⭐⭐⭐⭐⭐"
                                deviceInfo.mtuSize >= 256 -> "良好 ⭐⭐⭐⭐"
                                deviceInfo.mtuSize >= 128 -> "中等 ⭐⭐⭐"
                                deviceInfo.mtuSize >= 64 -> "较低 ⭐⭐"
                                else -> "默认 ⭐"
                            },
                            when {
                                deviceInfo.mtuSize >= 512 -> Color(0xFF4CAF50)
                                deviceInfo.mtuSize >= 256 -> Color(0xFF8BC34A)
                                deviceInfo.mtuSize >= 128 -> Color(0xFFFFC107)
                                deviceInfo.mtuSize >= 64 -> Color(0xFFFF9800)
                                else -> Color(0xFFFF5722)
                            }
                        )
                    }
                }

                // GATT服务信息
                item {
                    InfoCard(
                        title = "GATT服务",
                        icon = Icons.Filled.Apps,
                        iconColor = Color(0xFF9C27B0)
                    ) {
                        InfoRow("服务数量", "${deviceInfo.serviceCount} 个")
                        InfoRow("特征数量", "${deviceInfo.characteristicCount} 个")
                        InfoRow("描述符数量", "${deviceInfo.descriptorCount} 个")
                    }
                }

                // CCCD状态卡片
                item {
                    InfoCard(
                        title = "通知配置 (CCCD)",
                        icon = Icons.Filled.Notifications,
                        iconColor = Color(0xFFFF9800)
                    ) {
                        deviceInfo.cccdStates.forEach { (uuid, enabled) ->
                            val shortUuid = uuid.substring(4, 8)
                            InfoRow(
                                "0x$shortUuid",
                                if (enabled) "已启用 ✓" else "未启用",
                                if (enabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (deviceInfo.cccdStates.isEmpty()) {
                            Text(
                                "暂无通知特征",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // 特征列表
                item {
                    InfoCard(
                        title = "已发现的特征",
                        icon = Icons.Filled.List,
                        iconColor = Color(0xFF00BCD4)
                    ) {
                        deviceInfo.characteristics.forEach { char ->
                            CharacteristicItem(char)
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        if (deviceInfo.characteristics.isEmpty()) {
                            Text(
                                "未发现特征",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            content()
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            fontFamily = if (value.contains("0x") || value.contains("-")) FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
fun CharacteristicItem(char: CharacteristicInfo) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // UUID
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "UUID:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp)
            )
            Text(
                char.uuid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 属性
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "属性:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                char.properties.forEach { prop ->
                    PropertyChip(prop)
                }
            }
        }
    }
}

@Composable
fun PropertyChip(property: String) {
    Surface(
        color = when (property) {
            "READ" -> Color(0xFF2196F3).copy(alpha = 0.15f)
            "WRITE" -> Color(0xFF4CAF50).copy(alpha = 0.15f)
            "NOTIFY" -> Color(0xFFFF9800).copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            property,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = when (property) {
                "READ" -> Color(0xFF2196F3)
                "WRITE" -> Color(0xFF4CAF50)
                "NOTIFY" -> Color(0xFFFF9800)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Medium
        )
    }
}