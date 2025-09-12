package com.example.kioskhelper.presentation.kiosk.screen

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity : ComponentActivity() {
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        // ✅ 앱이 시스템 바 영역까지 그리도록
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ 네비게이션 바 투명 + 대비 스크림 제거 + 어두운 아이콘
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // 밝은 배경 위에 어두운 아이콘(제스처 네비에서도 적용)
            isAppearanceLightNavigationBars = true
        }

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
    }
}
