// FileUploadUtils.kt
package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width


// 辅助函数：格式化文件大小（在FileUploadManager.kt中删除这个函数）
fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        else -> String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
    }
}

// 已上传文件项组件
@Composable
fun UploadedFileItem(fileInfo: UploadFileInfo, fileType: FileType) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (fileType) {
                            FileType.MUSIC -> MaterialTheme.colorScheme.secondary
                            FileType.NOVEL -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (fileType) {
                        FileType.MUSIC -> Icons.Filled.MusicNote
                        FileType.NOVEL -> Icons.Filled.MenuBook
                        else -> Icons.Filled.FileCopy
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    fileInfo.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatFileSize(fileInfo.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已上传",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// 辅助函数：复制URI到文件
fun copyUriToFile(context: Context, uri: Uri, expectedExtension: String? = null): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // 获取文件名
        var fileName = "temp.$expectedExtension"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                cursor.moveToFirst()
                fileName = cursor.getString(nameIndex)
            }
        }

        // 检查文件扩展名
        if (expectedExtension != null) {
            val fileExt = fileName.substringAfterLast('.', "").lowercase()
            if (fileExt != expectedExtension) {
                inputStream.close()
                return null
            }
        }

        // 创建临时文件
        val tempFile = File(context.cacheDir, fileName)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}