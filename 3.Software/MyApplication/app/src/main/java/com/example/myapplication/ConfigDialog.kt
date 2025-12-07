// ConfigDialog.kt
package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
fun ServerConfigDialog(
    initialConfig: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    // 使用 MutableState 对象
    val serverIpState = remember { mutableStateOf(initialConfig.ip) }
    val serverPortState = remember { mutableStateOf(initialConfig.port) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "服务端配置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // IP地址输入
                OutlinedTextField(
                    value = serverIpState.value,
                    onValueChange = { serverIpState.value = it },
                    label = { Text("服务器IP地址") },
                    placeholder = { Text("例如：192.168.1.100") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = serverIpState.value.isNotEmpty() && !isValidIp(serverIpState.value)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 端口输入
                OutlinedTextField(
                    value = serverPortState.value,
                    onValueChange = { serverPortState.value = it },
                    label = { Text("服务器端口") },
                    placeholder = { Text("例如：8080") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = serverPortState.value.isNotEmpty() && !isValidPort(serverPortState.value)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val newConfig = ServerConfig(
                                ip = serverIpState.value,
                                port = serverPortState.value
                            )
                            onSave(newConfig)
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = isValidIp(serverIpState.value) && isValidPort(serverPortState.value)
                    ) {
                        Text("保存配置")
                    }
                }
            }
        }
    }
}

// 验证IP地址
private fun isValidIp(ip: String): Boolean {
    val pattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
    return pattern.matches(ip) || ip == "localhost"
}

// 验证端口
private fun isValidPort(port: String): Boolean {
    return try {
        val portNum = port.toInt()
        portNum in 1..65535
    } catch (e: NumberFormatException) {
        false
    }
}

@Composable
fun ApiConfigDialog(
    initialConfig: ApiConfig,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit
) {
    // 使用 MutableState 对象
    val baiduApiKeyState = remember { mutableStateOf(initialConfig.baiduApiKey) }
    val baiduSecretKeyState = remember { mutableStateOf(initialConfig.baiduSecretKey) }
    val youdaoApiKeyState = remember { mutableStateOf(initialConfig.youdaoApiKey) }
    val youdaoSecretKeyState = remember { mutableStateOf(initialConfig.youdaoSecretKey) }
    val aiKeyState = remember { mutableStateOf(initialConfig.aiKey) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "API配置",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 百度API配置区域
                Text(
                    "百度API配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = baiduApiKeyState.value,
                    onValueChange = { baiduApiKeyState.value = it },
                    label = { Text("百度 API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = baiduSecretKeyState.value,
                    onValueChange = { baiduSecretKeyState.value = it },
                    label = { Text("百度 Secret Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 有道API配置区域
                Text(
                    "有道API配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = youdaoApiKeyState.value,
                    onValueChange = { youdaoApiKeyState.value = it },
                    label = { Text("有道 API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = youdaoSecretKeyState.value,
                    onValueChange = { youdaoSecretKeyState.value = it },
                    label = { Text("有道 Secret Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI配置区域
                Text(
                    "AI配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = aiKeyState.value,
                    onValueChange = { aiKeyState.value = it },
                    label = { Text("AI Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 按钮区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val newConfig = ApiConfig(
                                baiduApiKey = baiduApiKeyState.value,
                                baiduSecretKey = baiduSecretKeyState.value,
                                youdaoApiKey = youdaoApiKeyState.value,
                                youdaoSecretKey = youdaoSecretKeyState.value,
                                aiKey = aiKeyState.value
                            )
                            onSave(newConfig)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存配置")
                    }
                }
            }
        }
    }
}