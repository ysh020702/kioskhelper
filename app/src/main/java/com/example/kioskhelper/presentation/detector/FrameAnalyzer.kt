package com.example.kioskhelper.presentation.detector

import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.kioskhelper.presentation.kiosk.KioskViewModel
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlin.math.max

class FrameAnalyzer(
    private val previewView: PreviewView,
    private val detector: Detector,
    private val uiButtonsProvider: () -> List<KioskViewModel.UiButton>,
    private val onMapped: (List<ButtonBox>) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val uiButtons = uiButtonsProvider()       // 최신 버튼 목록 스냅샷
            val detected = detector.detect(image, uiButtons) // image-space

            val mapped: List<ButtonBox> = mapToViewRects(
                imgW = image.width,
                imgH = image.height,
                rotation = image.imageInfo.rotationDegrees,
                viewW = previewView.width,
                viewH = previewView.height,
                rectsInImage = detected.map { it.rect },
                ids = detected.map { it.id }
            )

            previewView.post { onMapped(mapped) }
        } finally {
            image.close()
        }
    }
}

// 좌표변환 (FILL_CENTER 가정)
fun mapToViewRects(
    imgW: Int,
    imgH: Int,
    rotation: Int,
    viewW: Int,
    viewH: Int,
    rectsInImage: List<RectF>,
    ids: List<Int>
): List<ButtonBox> {
    val (srcW, srcH) = if (rotation % 180 == 0) imgW to imgH else imgH to imgW
    val scale = max(viewW.toFloat() / srcW, viewH.toFloat() / srcH)
    val dx = (viewW - srcW * scale) / 2f
    val dy = (viewH - srcH * scale) / 2f

    fun rotateRect(r: RectF): RectF = when (rotation) {
        0 -> RectF(r)
        90 -> RectF(srcH - r.bottom, r.left, srcH - r.top, r.right)
        180 -> RectF(srcW - r.right, srcH - r.bottom, srcW - r.left, srcH - r.top)
        270 -> RectF(r.top, srcW - r.right, r.bottom, srcW - r.left)
        else -> RectF(r)
    }

    fun toView(r: RectF) = RectF(
        dx + r.left * scale,
        dy + r.top * scale,
        dx + r.right * scale,
        dy + r.bottom * scale
    )

    return rectsInImage.mapIndexed { i, r -> ButtonBox(ids[i], toView(rotateRect(r))) }
}
