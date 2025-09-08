package com.example.kioskhelper.presentation.kiosk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView
import com.example.kioskhelper.utils.YuvToRgbConverter
import com.example.kioskhelper.vision.TfliteTaskObjectDetector
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max

class ObjectDetectAnalyzer(
    private val previewView: PreviewView,            // 런타임 생성 View → 직접 전달
    private val overlayView: DetectionOverlayView,   // 런타임 생성 View → 직접 전달
    private val detector: YoloV8TfliteInterpreter,  // Hilt로 상위에서 주입받아 전달
    private val yuvConverter: YuvToRgbConverter,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {


    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            android.util.Log.d("ANALYZER", "frame rot=${image.imageInfo.rotationDegrees}, src=${image.width}x${image.height}")

            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = if (rot != 0) rotate(src, rot) else src
            android.util.Log.d("ANALYZER", "bitmap=${rotated.width}x${rotated.height}, preview=${previewView.width}x${previewView.height}")
            // YOLO 추론(결과는 rotated 좌표계)
            val dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")

            // 상위 3개 점수 확인
            dets.sortedByDescending { it.score }.take(3).forEachIndexed { idx, d ->
                android.util.Log.d("ANALYZER", "top[$idx]=${"%.3f".format(d.score)} rect=${d.rect}")
            }

            // 화면(PreviewView.FILL_CENTER) 좌표로 변환
            val boxes = mapToPreview(rotated, dets.map { it.rect })
                .mapIndexed { i, r -> ButtonBox(id = i, rect = r) }

            val screenBoxes = mapToPreview(rotated, dets.map { it.rect })
            val finalBoxes = if (screenBoxes.isEmpty()) {
                // ✅ 탐지가 하나도 없을 때 중앙에 임시 박스 생성
                val w = previewView.width.toFloat()
                val h = previewView.height.toFloat()
                listOf(ButtonBox(999, RectF(w*0.4f, h*0.4f, w*0.6f, h*0.6f)))
            } else screenBoxes.mapIndexed { i, r -> ButtonBox(id = i, rect = r) }

            overlayView.post {
                overlayView.submitBoxes(finalBoxes)
                overlayView.invalidate()
            }
        }  catch (e: Throwable) {
            // ✅ 절대 삼키지 말고 찍자
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun mapToPreview(src: Bitmap, rects: List<RectF>): List<RectF> {
        val wSrc = src.width.toFloat(); val hSrc = src.height.toFloat()
        val wDst = previewView.width.toFloat(); val hDst = previewView.height.toFloat()
        if (wDst == 0f || hDst == 0f) return emptyList()
        val scale = max(wDst / wSrc, hDst / hSrc)
        val dx = (wDst - wSrc * scale) / 2f
        val dy = (hDst - hSrc * scale) / 2f
        return rects.map { r ->
            RectF(
                r.left * scale + dx, r.top * scale + dy,
                r.right * scale + dx, r.bottom * scale + dy
            )
        }
    }
}