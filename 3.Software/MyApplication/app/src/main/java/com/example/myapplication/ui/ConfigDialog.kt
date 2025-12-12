package com.example.myapplication.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.myapplication.config.ApiConfig
import com.example.myapplication.config.ServerConfig

/**
 * API配置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigDialog(
    initialConfig: ApiConfig,
    onDismiss: () -> Unit,
    onSave: (ApiConfig) -> Unit
) {
    // 百度API配置
    var baiduApiKey by remember { mutableStateOf(initialConfig.baiduApiKey) }
    var baiduSecretKey by remember { mutableStateOf(initialConfig.baiduSecretKey) }
    var showBaiduApiKey by remember { mutableStateOf(false) }
    var showBaiduSecretKey by remember { mutableStateOf(false) }

    // 有道API配置
    var youdaoApiKey by remember { mutableStateOf(initialConfig.youdaoApiKey) }
    var youdaoSecretKey by remember { mutableStateOf(initialConfig.youdaoSecretKey) }
    var showYoudaoApiKey by remember { mutableStateOf(false) }
    var showYoudaoSecretKey by remember { mutableStateOf(false) }

    // AI API配置（修复：使用aiKey替代aiApiKey）
    var aiKey by remember { mutableStateOf(initialConfig.aiKey) }
    var showAiKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "API 配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 百度API配置卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "百度图片增强 API",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "用于图片增强和OCR处理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )

                        // 百度API Key
                        OutlinedTextField(
                            value = baiduApiKey,
                            onValueChange = { baiduApiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("请输入百度API Key") },
                            leadingIcon = {
                                Icon(Icons.Filled.VpnKey, "API Key")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showBaiduApiKey = !showBaiduApiKey }) {
                                    Icon(
                                        if (showBaiduApiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        "切换显示"
                                    )
                                }
                            },
                            visualTransformation = if (showBaiduApiKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 百度Secret Key
                        OutlinedTextField(
                            value = baiduSecretKey,
                            onValueChange = { baiduSecretKey = it },
                            label = { Text("Secret Key") },
                            placeholder = { Text("请输入百度Secret Key") },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, "Secret Key")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showBaiduSecretKey = !showBaiduSecretKey }) {
                                    Icon(
                                        if (showBaiduSecretKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        "切换显示"
                                    )
                                }
                            },
                            visualTransformation = if (showBaiduSecretKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // 有道API配置卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CropFree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "有道图片分割 API",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "用于图片区域分割和识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )

                        // 有道API Key
                        OutlinedTextField(
                            value = youdaoApiKey,
                            onValueChange = { youdaoApiKey = it },
                            label = { Text("应用ID") },
                            placeholder = { Text("请输入有道应用ID") },
                            leadingIcon = {
                                Icon(Icons.Filled.VpnKey, "应用ID")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showYoudaoApiKey = !showYoudaoApiKey }) {
                                    Icon(
                                        if (showYoudaoApiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        "切换显示"
                                    )
                                }
                            },
                            visualTransformation = if (showYoudaoApiKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        // 有道Secret Key
                        OutlinedTextField(
                            value = youdaoSecretKey,
                            onValueChange = { youdaoSecretKey = it },
                            label = { Text("应用密钥") },
                            placeholder = { Text("请输入有道应用密钥") },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, "应用密钥")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showYoudaoSecretKey = !showYoudaoSecretKey }) {
                                    Icon(
                                        if (showYoudaoSecretKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        "切换显示"
                                    )
                                }
                            },
                            visualTransformation = if (showYoudaoSecretKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                // AI API配置卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI 分析 API",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "用于AI图片内容分析和科目判断",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )

                        // AI Key（修复：正确引用aiKey）
                        OutlinedTextField(
                            value = aiKey,
                            onValueChange = { aiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("请输入AI API Key (sk-...)") },
                            leadingIcon = {
                                Icon(Icons.Filled.VpnKey, "API Key")
                            },
                            trailingIcon = {
                                IconButton(onClick = { showAiKey = !showAiKey }) {
                                    Icon(
                                        if (showAiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        "切换显示"
                                    )
                                }
                            },
                            visualTransformation = if (showAiKey)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text(
                            "获取方式：访问 https://www.chatanywhere.tech/",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ApiConfig(
                            baiduApiKey = baiduApiKey.trim(),
                            baiduSecretKey = baiduSecretKey.trim(),
                            youdaoApiKey = youdaoApiKey.trim(),
                            youdaoSecretKey = youdaoSecretKey.trim(),
                            aiKey = aiKey.trim()  // 修复：使用aiKey
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * 服务器配置对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigDialog(
    initialConfig: ServerConfig,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    var ip by remember { mutableStateOf(initialConfig.ip) }
    var port by remember { mutableStateOf(initialConfig.port) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "服务器配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "后端服务器地址",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = ip,
                            onValueChange = { ip = it },
                            label = { Text("IP地址") },
                            placeholder = { Text("例如: 192.168.1.100") },
                            leadingIcon = {
                                Icon(Icons.Filled.Computer, "IP")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("端口") },
                            placeholder = { Text("例如: 8080") },
                            leadingIcon = {
                                Icon(Icons.Filled.SettingsEthernet, "端口")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(ServerConfig(ip = ip.trim(), port = port.trim()))
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("保存")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("取消")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}