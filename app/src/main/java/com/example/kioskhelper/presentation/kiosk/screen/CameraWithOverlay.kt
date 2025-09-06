package com.example.kioskhelper.presentation.kiosk.screen

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView

@Composable
fun CameraWithOverlay(
    modifier: Modifier = Modifier,
    bindUseCases: (PreviewView) -> Unit,        // CameraX 바인딩을 외부에서
    boxes: List<ButtonBox>,                     // 감지 결과(뷰모델 state)
    highlightIds: List<Int>,
    ambiguous: Boolean
) {
    Box(modifier) {
        // 1) CameraX PreviewView
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    post { bindUseCases(this) } // lifecycleOwner에서 실제 바인딩
                }
            }
        )

        // 2) DetectionOverlayView (이미 프로젝트에 있는 뷰)
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { ctx ->
                DetectionOverlayView(ctx).apply {
                    submitBoxes(boxes)
                    highlight(highlightIds, ambiguous)
                }
            },
            update = { overlay ->
                overlay.submitBoxes(boxes)
                if (highlightIds.isEmpty()) overlay.clearHighlight()
                else overlay.highlight(highlightIds, ambiguous)
            }
        )
    }
}
