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

    //추가
    private val MIN_SIDE = 24f      // 너무 작은 박스는 분류 품질이 낮으니 건너뛰기
    private val SCORE_THR = 0.40f
    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            android.util.Log.d("ANALYZER",
                "frame rot=${image.imageInfo.rotationDegrees}, src=${image.width}x${image.height}")

            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = if (rot != 0) rotate(src, rot) else src
            android.util.Log.d("ANALYZER",
                "bitmap=${rotated.width}x${rotated.height}, preview=${previewView.width}x${previewView.height}")

            // 1) YOLO 탐지 (rotated 좌표계)
            var dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")
            dets = dets.sortedByDescending { it.score }.take(20)

            // 2) 각 박스 crop → 역할 분류(predictRole) → ButtonBox로 변환
            //    (이 분류기는 score를 안 내보내므로 score 필터는 없음. 너무 작은 박스만 필터)
            val boxes: List<ButtonBox> = dets.mapIndexedNotNull { idx, det ->
                val r = det.rect
                if (r.width() < MIN_SIDE || r.height() < MIN_SIDE) return@mapIndexedNotNull null

                val crop = safeCrop(rotated, r)
                val label = try {
                    roleClf.predictRole(crop)   // ← 현재 클래스 API에 맞게 호출
                } catch (t: Throwable) {
                    android.util.Log.e("RoleClf", "predictRole failed: rect=$r", t)
                    crop.recycle()
                    return@mapIndexedNotNull null
                } finally {
                    // 분류가 끝났으니 바로 회수(메모리 압박 방지)
                    crop.recycle()
                }

                android.util.Log.d("RoleClf", "box#$idx label=$label rect=$r")

                // ButtonBox가 (id, rect)만 가진다면 그대로:
                ButtonBox(id = idx, rect = r)

                // 만약 ButtonBox에 text가 있다면 이렇게 쓸 수 있어요:
                // ButtonBox(id = idx, rect = r, text = label)
            }

            // 3) 오버레이 갱신
            overlayView.post {
                overlayView.setSourceSize(rotated.width, rotated.height)
                overlayView.submitBoxes(boxes)
                overlayView.invalidate()
            }

            // 4) 비트맵 정리 (매 프레임 메모리 안전)
            if (rot != 0) src.recycle()
            rotated.recycle()

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }



    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
    private fun safeCrop(src: Bitmap, rect: RectF): Bitmap {
        val L = rect.left.coerceAtLeast(0f).toInt()
        val T = rect.top.coerceAtLeast(0f).toInt()
        val R = rect.right.coerceAtMost(src.width.toFloat()).toInt()
        val B = rect.bottom.coerceAtMost(src.height.toFloat()).toInt()
        val w = (R - L).coerceAtLeast(1)
        val h = (B - T).coerceAtLeast(1)
        return Bitmap.createBitmap(src, L, T, w, h)
    }

}