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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusDot(on = ui.statusDotOn)
                        Text(
                            text = "키오스크 도우미",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
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
                        }
                    }
                }
            )
        },
        bottomBar = {
            // BottomBarModern 가 modifier 파라미터 없으면 이렇게 감싸 주세요
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                BottomBarModern(
                    listening = ui.listening,
                    // ⬇️ "하이라이트만 취소" 로 변경
                    onCancel = kioskVm::onCancel,
                    onMicClick = kioskVm::onMicToggle,
                    finalText = ui.finalText
                )
            }
        }
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            AnimatedVisibility(visible = ui.tip.isNotBlank()) {
                Column {
                    GuideBannerStylish(ui.tip)
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

                // 🔹 음성인식 메시지 오버레이 (상단 중앙)
                SttOverlay(
                    ui = ui,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp)
                )
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
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info, // 강조 의미를 주는 아이콘
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2
            )
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

// 2) 상단 오버레이: 음성 인식 메시지(최종을 크고 굵게, 없으면 partial 가볍게)
@Composable
fun SttOverlay(ui: KioskViewModel.UiState, modifier: Modifier = Modifier) {
    val hasFinal = ui.finalText.isNotBlank()
    val hasPartial = ui.partialText.isNotBlank()
    val finalText = ui.finalText

    if (!hasFinal && !hasPartial) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (hasFinal) {
                // ✅ 사용자가 찾으려는 버튼 문구: 명확하고 굵게
                Text(
                    text = "'$finalText' 인식 중...",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (hasPartial) {
                // 진행 중 안내(가볍게)
                Text(
                    text = ui.partialText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 하단 바를 떠 있게 (접근성↑) */
@Composable
private fun BottomBarModern(
    listening: Boolean,
    onCancel: () -> Unit,
    onMicClick: () -> Unit,
    finalText: String
) {
    val haptics = LocalHapticFeedback.current
    val micLabel = if (listening) "듣는 중" else "말하기"

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ① 좌측: 취소 버튼
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(56.dp),
            colors = if (!finalText.isBlank()) {
                ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor   = MaterialTheme.colorScheme.primary
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            }
        ) {
            Icon(imageVector = Icons.Rounded.Close, contentDescription = "취소")
            Spacer(Modifier.width(8.dp))
            Text(
                "취소",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        }

        // ② 중앙: 큰 원형 마이크 버튼
        Button(
            onClick = {
                onMicClick()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(102.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            ),
            colors = if (listening)
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            else
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = micLabel,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    micLabel,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
