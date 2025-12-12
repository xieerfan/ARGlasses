package com.example.myapplication

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// ==================== 小米风格配色方案 ====================

// 根据时间自动选择主题
fun shouldUseDarkTheme(): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hour >= 18 || hour < 7
}

// 浅色主题 - 小米风格（干净、轻盈）
private val MiLightColorScheme = lightColorScheme(
    primary = Color(0xFF1F97FF),          // 小米蓝
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F4FF),
    onPrimaryContainer = Color(0xFF003E82),

    secondary = Color(0xFF626262),        // 灰色
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEEEE),
    onSecondaryContainer = Color(0xFF212121),

    tertiary = Color(0xFFFFA500),         // 橙色
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5CC),
    onTertiaryContainer = Color(0xFF663D00),

    error = Color(0xFFD32F2F),            // 红色
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFF840029),

    background = Color(0xFFFAFAFA),       // 极浅灰
    onBackground = Color(0xFF212121),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),   // 浅灰背景
    onSurfaceVariant = Color(0xFF616161),

    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
)

// 深色主题 - 小米风格（深蓝、现代）
private val MiDarkColorScheme = darkColorScheme(
    primary = Color(0xFF66B1FF),          // 浅蓝
    onPrimary = Color(0xFF001A4D),
    primaryContainer = Color(0xFF004A99),
    onPrimaryContainer = Color(0xFFE8F4FF),

    secondary = Color(0xFF9E9E9E),        // 浅灰
    onSecondary = Color(0xFF212121),
    secondaryContainer = Color(0xFF424242),
    onSecondaryContainer = Color(0xFFEEEEEE),

    tertiary = Color(0xFFFFB74D),         // 浅橙
    onTertiary = Color(0xFF331900),
    tertiaryContainer = Color(0xFF994C00),
    onTertiaryContainer = Color(0xFFFFE5CC),

    error = Color(0xFFFF6B6B),            // 浅红
    onError = Color(0xFF5A0000),
    errorContainer = Color(0xFF7A0014),
    onErrorContainer = Color(0xFFFFEBEE),

    background = Color(0xFF0F0F0F),       // 极深灰/黑
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1A1A1A),          // 深灰
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2A2A2A),   // 深灰背景
    onSurfaceVariant = Color(0xFFBDBDBD),

    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF3F3F3F),
)

// ==================== Material 3 排版（保持原样）====================

private val MiTypography = Typography()

// ==================== 主题Composable ====================

@Composable
fun AppTheme(
    darkTheme: Boolean = shouldUseDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MiDarkColorScheme else MiLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MiTypography,
        content = content
    )
}