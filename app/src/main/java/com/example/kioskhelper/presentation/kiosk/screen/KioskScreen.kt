package com.example.kioskhelper.presentation.kiosk.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kioskhelper.presentation.detector.Detector
import com.example.kioskhelper.presentation.detector.NoopDetector
import com.example.kioskhelper.presentation.kiosk.KioskViewModel
import com.example.kioskhelper.presentation.kiosk.VisionViewModel
import com.example.kioskhelper.presentation.kiosk.setupCamera

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    kioskVm: KioskViewModel = hiltViewModel(),
    visionVm: VisionViewModel = hiltViewModel() // ⬅️ 신규
) {
    val ui by kioskVm.ui.collectAsStateWithLifecycle()
    val vUi by visionVm.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 실제 Detector 구현(Yolo/MlKit)로 교체
    val detector: Detector = remember { NoopDetector() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(on = ui.statusDotOn)
                        Spacer(Modifier.width(8.dp))
                        Text("Kiosk Helper")
                    }
                }
            )
                 },
        bottomBar = { BottomBar(ui.listening, kioskVm::onCancel, kioskVm::onMicToggle) }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (!ui.tip.isNullOrBlank()) { GuideBanner(ui.tip!!); Spacer(Modifier.height(8.dp)) }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraWithOverlay(
                    modifier = Modifier.fillMaxSize(),
                    bindUseCases = { previewView ->
                        setupCamera(
                            previewView = previewView,
                            lifecycleOwner = lifecycleOwner,
                            detector = detector,
                            // ✅ Vision 쪽 매칭에 필요한 버튼 목록은 KioskVM의 현재 스냅샷을 공급
                            uiButtonsProvider = { kioskVm.ui.value.buttons },
                            // ✅ 화면좌표 박스는 VisionVM이 보유
                            onDetections = visionVm::onDetections
                        )
                        visionVm.setRunning(true)
                    },
                    boxes = vUi.boxes,
                    highlightIds = ui.highlightedIds,
                    ambiguous = ui.highlightedIds.size >= 2
                )
            }

            // 디버그 패널(기존)
            Column(Modifier.padding(12.dp)) {
                if (!ui.sttError.isNullOrBlank()) Text("STT 오류: ${ui.sttError}", color = Color.Red)
                Text("Partial: ${ui.partialText}")
                Text("Final  : ${ui.finalText}")
                Text("HL IDs : ${ui.highlightedIds}")
                if (ui.ttsSpeaking) Text("TTS: speaking…")
            }
        }
    }
}
@Composable private fun StatusDot(on: Boolean) {
    val color = if (on) Color(0xFF2ECC71) else Color(0xFFB0B0B0)
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
}

@Composable private fun GuideBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(text, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun BottomBar(
    listening: Boolean,
    onCancel: () -> Unit,
    onMicClick: () -> Unit   // ← 토글: 한 번 누르면 시작/종료
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MicToggleButton(listening = listening, onMicClick = onMicClick)

        OutlinedButton(onClick = onCancel, enabled = listening) {
            Text("취소")
        }
    }
}

@Composable
private fun MicToggleButton(
    listening: Boolean,
    onMicClick: () -> Unit
) {
    val label = if (listening) "듣는 중… 탭하여 종료" else "탭하여 음성인식 시작"
    Button(
        onClick = onMicClick, // ← 한 번 탭으로 토글
        colors = ButtonDefaults.buttonColors(
            containerColor = if (listening) Color(0xFF2ECC71) else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(label)
    }
}