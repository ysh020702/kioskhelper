package com.example.kioskhelper.presentation.kiosk

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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

@ActivityRetainedScoped
class ObjectDetectAnalyzer @Inject constructor(
    private val detector: YoloV8TfliteInterpreter,
    private val roleClf: IconRoleClassifier,
    private val previewView: PreviewView,
    private val overlayView: DetectionOverlayView,
    private val yuvConverter: YuvToRgbConverter,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {

    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ===== Helpers ============================================================
    private fun expandRectToMinSize(
        r: RectF,
        minW: Float,
        minH: Float,
        padFrac: Float,
        imgW: Int,
        imgH: Int
    ): RectF {
        val w = r.width()
        val h = r.height()

        val padX = max(w * padFrac, 2f)
        val padY = max(h * padFrac, 2f)

        var left = r.left - padX
        var top = r.top - padY
        var right = r.right + padX
        var bottom = r.bottom + padY

        if (right - left < minW) {
            val add = (minW - (right - left)) / 2f
            left -= add; right += add
        }
        if (bottom - top < minH) {
            val add = (minH - (bottom - top)) / 2f
            top -= add; bottom += add
        }

        left = left.coerceIn(0f, imgW.toFloat())
        top = top.coerceIn(0f, imgH.toFloat())
        right = right.coerceIn(0f, imgW.toFloat())
        bottom = bottom.coerceIn(0f, imgH.toFloat())
        if (right <= left) right = min(imgW.toFloat(), left + minW)
        if (bottom <= top) bottom = min(imgH.toFloat(), top + minH)

        return RectF(left, top, right, bottom)
    }

    private fun upscaleForOcr(src: Bitmap, targetShort: Int = 96, maxLong: Int = 512): Bitmap {
        val shortSide = min(src.width, src.height)
        val longSide = max(src.width, src.height)
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    private fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val width = rect.width().toInt().coerceAtMost(src.width - left).coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtMost(src.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg == 0) return b
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
    // ==========================================================================

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            android.util.Log.d("ANALYZER",
                "frame rot=${image.imageInfo.rotationDegrees}, src=${image.width}x${image.height}")

            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = rotate(src, rot)
            if (rot != 0) src.recycle()

            android.util.Log.d("ANALYZER",
                "bitmap=${rotated.width}x${rotated.height}, preview=${previewView.width}x${previewView.height}")

            var dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")
            dets = dets.sortedByDescending { it.score }.take(20)

            dets.forEachIndexed { idx, det ->
                val expanded = expandRectToMinSize(
                    det.rect,
                    minW = 32f,
                    minH = 32f,
                    padFrac = 0.15f,
                    imgW = rotated.width,
                    imgH = rotated.height
                )
                val rawCrop = cropBitmap(rotated, expanded)
                val ocrCrop = upscaleForOcr(rawCrop, targetShort = 96, maxLong = 512)

                // OCR → fallback Icon classifier
                val inputImg = InputImage.fromBitmap(ocrCrop, 0)
                textRecognizer.process(inputImg)
                    .addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        val role = if (text.isNotEmpty()) {
                            android.util.Log.i("LABEL",
                                "[$idx] OCR  label='$text' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}")
                            text
                        } else {
                            val r = roleClf.predictRole(rawCrop)
                            android.util.Log.d("LABEL",
                                "[$idx] ICON label='$r' rect=$expanded reason=ocr-empty raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}")
                            r
                        }

                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }

                        // 메모리 정리
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
                    .addOnFailureListener { e ->
                        val role = roleClf.predictRole(rawCrop)
                        android.util.Log.w("LABEL",
                            "[$idx] ICON label='$role' rect=$expanded reason=ocr-failed: ${e.message} raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}")

                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }

                        // 메모리 정리
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
            }

            // 주의: 위 OCR 콜백이 비동기라서 여기서 rotated를 바로 recycle하면 안 됨.
            // (width/height만 쓰니 안전하게 쓰고 싶다면 사이즈만 캐시해서 넘기는 구조로 바꾼 뒤 recycle 가능)
            // 지금은 GC에 맡김. 필요하면 Analyzer 외부에서 프레임 레이트로 압박을 낮춰줘.

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }
}
