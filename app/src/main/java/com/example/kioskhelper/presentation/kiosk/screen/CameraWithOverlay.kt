package com.example.kioskhelper.presentation.kiosk.screen

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView

@Composable
fun CameraWithOverlay(
    modifier: Modifier = Modifier,
    // 🔄 변경: overlay도 함께 넘겨 받도록
    bindUseCases: (previewView: PreviewView, overlayView: DetectionOverlayView) -> Unit,
    // 🔄 변경: boxes 제거 (분석기가 overlay에 직접 그리므로)
    highlightIds: List<Int>,
    ambiguous: Boolean
) {
    val context = LocalContext.current

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var overlayRef by remember { mutableStateOf<DetectionOverlayView?>(null) }

    Box(modifier) {
        // 1) CameraX PreviewView
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewRef = this
                }
            },
            update = { previewViewRef = it }
        )

        // 2) DetectionOverlayView
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                DetectionOverlayView(ctx).apply {
                    overlayRef = this
                }
            },
            update = { overlay ->
                overlayRef = overlay
                // ✅ 하이라이트는 계속 외부 상태로 갱신
                overlay.highlight(highlightIds, ambiguous)
            }
        )
    }

    // 3) 둘 다 준비되면 한 번만 바인딩
    LaunchedEffect(previewViewRef, overlayRef) {
        val pv = previewViewRef ?: return@LaunchedEffect
        val ov = overlayRef ?: return@LaunchedEffect
        bindUseCases(pv, ov)
    }
}
