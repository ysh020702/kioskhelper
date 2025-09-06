package com.example.kioskhelper.presentation.kiosk

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.kioskhelper.presentation.kiosk.screen.KioskScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class KioskActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) 권한 요청만 Activity에서
        requestPermissions.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ))

        // 2) 나머지는 전부 Compose Screen/VM에서 처리
        setContent {
            KioskScreen() // ← 네가 만든 Compose 스크린(이미 VM을 내부에서 hiltViewModel로 가져간다고 가정)
        }
    }
}
