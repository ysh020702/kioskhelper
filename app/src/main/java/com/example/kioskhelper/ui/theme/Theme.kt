package com.example.kioskhelper.ui.theme

import android.app.Activity
import android.hardware.lights.Light
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ---- Light scheme ----
private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.Black,       // 밝은 색 위 텍스트는 검정이 가독성 ↑
    secondary = Teal400,
    onSecondary = Color.Black,
    tertiary = Teal500,
    onTertiary = Color.Black,

    background = White,
    onBackground = NearBlack,
    surface = White,
    onSurface = NearBlack,

    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF2B2B2B),
    outline = OutlineG,
)

// ---- Dark scheme ----
// 다크에서는 포인트 컬러 유지 + 표면/배경만 어둡게
private val DarkColors = darkColorScheme(
    primary = Teal600,
    onPrimary = Color.Black,       // 이 색상은 다크에서도 검정이 대비 좋음
    secondary = Teal400,
    onSecondary = Color.Black,
    tertiary = Teal500,
    onTertiary = Color.Black,

    background = Color(0xFF0F1112),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121415),
    onSurface = Color(0xFFEDEDEE),

    surfaceVariant = Color(0xFF2A2D2F),
    onSurfaceVariant = Color(0xFFD9DADC),
    outline = Color(0xFF414649),
)

@Composable
fun KioskhelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}