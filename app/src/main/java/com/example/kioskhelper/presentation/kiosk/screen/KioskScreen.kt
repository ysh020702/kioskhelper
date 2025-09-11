// KioskScreen.kt
package com.example.kioskhelper.presentation.kiosk.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kioskhelper.presentation.kiosk.KioskViewModel
import com.example.kioskhelper.presentation.kiosk.VisionViewModel
import com.example.kioskhelper.presentation.kiosk.setupCamera
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    kioskVm: KioskViewModel = hiltViewModel(),
    visionVm: VisionViewModel = hiltViewModel(),
) {
    val ui by kioskVm.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // 토스트 이벤트
    LaunchedEffect(Unit) {
        kioskVm.toast.collectLatest { toast ->
            when (toast) {
                is KioskViewModel.ToastEvent.ShowToast ->
                    Toast.makeText(context, toast.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(on = ui.statusDotOn)
                        Spacer(Modifier.width(8.dp))
                        Text("키오스크 도우미")
                    }
                },
                actions = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPill(
                                text = if (ui.listening) "🎙 듣는 중" else "대기",
                                tone = if (ui.listening) PillTone.Positive else PillTone.Neutral
                            )
                            StatusPill(
                                text = if (ui.ttsSpeaking) "🗣 TTS" else "TTS Off",
                                tone = if (ui.ttsSpeaking) PillTone.Info else PillTone.Neutral
                            )
                            StatusPill(
                                text = "찾은 버튼: ${ui.highlightedIds.size}개",
                                tone = if (ui.highlightedIds.size >= 2) PillTone.Warn else PillTone.Neutral
                            )
                        }
                    }

                }
            )
        },
        bottomBar = {
            BottomBarModern(
                listening = ui.listening,
                onCancel = kioskVm::onCancel,
                onMicClick = kioskVm::onMicToggle
            )
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            AnimatedVisibility(visible = !ui.tip.isNullOrBlank()) {
                Column {
                    GuideBannerStylish(ui.tip!!)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                // 카메라 + 오버레이
                CameraWithOverlay(
                    modifier = Modifier.fillMaxSize(),
                    bindUseCases = { previewView, overlayView ->
                        setupCamera(
                            previewView = previewView,
                            overlayView = overlayView,
                            lifecycleOwner = lifecycleOwner,
                            detector = visionVm.detector,
                            roleClf = visionVm.roleClf,
                            kioskViewModel = kioskVm,
                            throttleMs = 0
                        )
                    },
                    highlightIds = ui.highlightedIds,
                    ambiguous = ui.highlightedIds.size >= 2
                )

                // ✅ 오른쪽 위 코너 상태 카드
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    StatusCornerCard(
                        currentLabel = ui.currentHighlightLabel
                    )
                }
            }

            // 디버그 스냅샷(간단 버전)
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (!ui.sttError.isNullOrBlank())
                    Text("STT 오류: ${ui.sttError}", color = Color.Red)
                if (ui.partialText.isNotBlank())
                    Text("Partial: ${ui.partialText}")
                if (ui.finalText.isNotBlank())
                    Text("Final  : ${ui.finalText}")
            }
        }
    }
}

/* -------------------- 작은 컴포넌트 -------------------- */

@Composable private fun StatusDot(on: Boolean) {
    val color = if (on) Color(0xFF2ECC71) else Color(0xFFB0B0B0)
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
}

/** 아이콘 + 색감 있는 가이드 배너 */
@Composable private fun GuideBannerStylish(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StatusCornerCard(
    currentLabel: String?
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Column(Modifier.padding(10.dp)) {
            // 현재 강조 중 라벨
            if (!currentLabel.isNullOrBlank()) {
                Text(
                    text = "$currentLabel 강조 중",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private enum class PillTone { Positive, Warn, Info, Neutral }

@Composable
private fun StatusPill(text: String, tone: PillTone) {
    val bg = when (tone) {
        PillTone.Positive -> Color(0x332ECC71)
        PillTone.Warn     -> Color(0x33F1C40F)
        PillTone.Info     -> Color(0x334AA3F0)
        PillTone.Neutral  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val bd = when (tone) {
        PillTone.Positive -> Color(0xFF2ECC71)
        PillTone.Warn     -> Color(0xFFF1C40F)
        PillTone.Info     -> Color(0xFF4AA3F0)
        PillTone.Neutral  -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

/** 하단 바를 떠 있게 (접근성↑) */
@Composable
private fun BottomBarModern(
    listening: Boolean,
    onCancel: () -> Unit,
    onMicClick: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MicToggleButton(listening = listening, onMicClick = onMicClick)
            OutlinedButton(onClick = onCancel, enabled = listening) {
                Text("취소")
            }
        }
    }
}

@Composable private fun MicToggleButton( listening: Boolean, onMicClick: () -> Unit ) {
    val label = if (listening) "듣는 중… 탭하여 종료" else "탭하여 음성인식 시작"
    Button( onClick = onMicClick, // ← 한 번 탭으로 토글
            colors = ButtonDefaults
                .buttonColors( containerColor = if (listening) Color(0xFF2ECC71) else MaterialTheme.colorScheme.primary ) )
    {
        Text(label)
    }
}

