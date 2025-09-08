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
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import com.example.kioskhelper.vision.IconRoleClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max

class ObjectDetectAnalyzer @Inject constructor(
    private val detector: YoloV8TfliteInterpreter,
    private val roleClf: IconRoleClassifier,
    private val previewView: PreviewView,            // 런타임 생성 View → 직접 전달
    private val overlayView: DetectionOverlayView,  // Hilt로 상위에서 주입받아 전달
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
            var dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")

            dets = dets.sortedByDescending { it.score }.take(20)

            // 3) 각 박스 crop → 역할 분류 → ButtonBox로 변환
            val boxes: List<ButtonBox> = dets.mapIndexed { idx, det ->
                ButtonBox(
                    id = idx,        // 탐지만 할 경우 단순 index id 부여
                    rect = det.rect  // YOLO가 준 bounding box 그대로 사용
                )
            }

            overlayView.post {
                overlayView.setSourceSize(rotated.width, rotated.height) // ✅ 추가
                overlayView.submitBoxes(boxes)
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

}