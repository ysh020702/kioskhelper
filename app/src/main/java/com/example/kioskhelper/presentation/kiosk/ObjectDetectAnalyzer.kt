package com.example.kioskhelper.presentation.kiosk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.example.kioskhelper.core.Utils.cropBitmap
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView
import com.example.kioskhelper.utils.YuvToRgbConverter
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import com.example.kioskhelper.vision.IconRoleClassifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/*
class ObjectDetectAnalyzer @Inject constructor(
    private val detector: YoloV8TfliteInterpreter,
    private val roleClf: IconRoleClassifier,
    private val previewView: PreviewView,            // 런타임 생성 View → 직접 전달
    private val overlayView: DetectionOverlayView,  // Hilt로 상위에서 주입받아 전달
    private val yuvConverter: YuvToRgbConverter,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {


    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)
    // ✅ OCR 엔진은 한 번만 생성해서 재사용
    private val textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

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
            val boxes = dets.mapIndexed { idx, det ->
                // 탐지된 영역 잘라내기
                val crop = cropBitmap(rotated, det.rect)

                var role = "unknown"

                try {
                    // 1) OCR 실행
                    val inputImg = InputImage.fromBitmap(crop, 0)
                    val task = textRecognizer.process(inputImg)

                    // OCR은 비동기 → 콜백에서 role 결정
                    task.addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isNotEmpty()) {
                            android.util.Log.d("OCR", "OCR 성공: '$text'")
                            role = text
                        } else {
                            android.util.Log.d("OCR", "OCR 결과 없음 → IconClassifier 사용")
                            role = roleClf.predictRole(crop)
                        }

                        // ✅ 최종 ButtonBox 전달
                        val buttonBox = ButtonBox(
                            id = idx,
                            rect = det.rect,
                            label = role   // ButtonBox에 label 필드가 있다고 가정
                        )
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }.addOnFailureListener {

                        // OCR 자체 실패 → IconClassifier로 대체
                        role = roleClf.predictRole(crop)
                        android.util.Log.e("OCR", "OCR 실패 → IconClassifier 사용")
                        val buttonBox = ButtonBox(
                            id = idx,
                            rect = det.rect,
                            label = role
                        )
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    // OCR 실행 중 예외 발생 시 fallback
                    role = roleClf.predictRole(crop)
                }

                // 기본 반환 (OCR 콜백에서 최종 업데이트됨)
                ButtonBox(
                    id = idx,
                    rect = det.rect,
                    label = role
                )
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }

    // ✅ RectF 영역을 잘라내는 헬퍼 함수
    private fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val width = rect.width().toInt().coerceAtMost(src.width - left)
        val height = rect.height().toInt().coerceAtMost(src.height - top)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

}
 */
/*
class ObjectDetectAnalyzer @Inject constructor(
    private val detector: YoloV8TfliteInterpreter,
    private val roleClf: IconRoleClassifier,
    private val previewView: PreviewView,            // 런타임 생성 View → 직접 전달
    private val overlayView: DetectionOverlayView,   // Hilt로 상위에서 주입받아 전달
    private val yuvConverter: YuvToRgbConverter,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {

    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    // ✅ OCR 엔진은 한 번만 생성해서 재사용
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

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

            // 1) YOLO 추론(결과는 rotated 좌표계)
            var dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")
            dets = dets.sortedByDescending { it.score }.take(20)

            // 2) 각 박스 crop → OCR → 필요 시 아이콘분류 → 오버레이
            dets.forEachIndexed { idx, det ->
                val crop = cropBitmap(rotated, det.rect)
                var role = "unknown"

                try {
                    val inputImg = InputImage.fromBitmap(crop, 0)
                    val task = textRecognizer.process(inputImg)

                    task.addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isNotEmpty()) {
                            // ★추가: OCR로 라벨 결정 로그
                            role = text
                            android.util.Log.d("LABEL", "[$idx] OCR label='$role' rect=${det.rect} (len=${role.length})")
                        } else {
                            // ★추가: OCR 내용 없음 → 아이콘 분류기
                            role = roleClf.predictRole(crop)
                            android.util.Log.d("LABEL", "[$idx] ICON label='$role' rect=${det.rect} reason=ocr-empty")
                        }

                        // 최종 ButtonBox 반영
                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }.addOnFailureListener { e ->
                        // ★추가: OCR 실패 → 아이콘 분류기
                        role = roleClf.predictRole(crop)
                        android.util.Log.w("LABEL", "[$idx] ICON label='$role' rect=${det.rect} reason=ocr-failed: ${e.message}")

                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    // ★추가: 실행 중 예외 → 아이콘 분류기
                    role = roleClf.predictRole(crop)
                    android.util.Log.e("LABEL", "[$idx] ICON label='$role' rect=${det.rect} reason=exception: ${e.message}")

                    val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                    overlayView.post {
                        overlayView.setSourceSize(rotated.width, rotated.height)
                        overlayView.submitBoxes(listOf(buttonBox))
                        overlayView.invalidate()
                    }
                }
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }

    // ✅ RectF 영역을 잘라내는 헬퍼 함수
    private fun cropBitmap(src: Bitmap, rect: RectF): Bitmap {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val width = rect.width().toInt().coerceAtMost(src.width - left).coerceAtLeast(1)
        val height = rect.height().toInt().coerceAtMost(src.height - top).coerceAtLeast(1)
        return Bitmap.createBitmap(src, left, top, width, height)
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}

 */

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
    // ✅ OCR 전에 박스를 최소 크기 이상으로 확장 (패딩 포함)
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

        // 기본 패딩
        val padX = max(w * padFrac, 2f)
        val padY = max(h * padFrac, 2f)

        var left = r.left - padX
        var top = r.top - padY
        var right = r.right + padX
        var bottom = r.bottom + padY

        // 최소 가로/세로 보장
        if (right - left < minW) {
            val add = (minW - (right - left)) / 2f
            left -= add; right += add
        }
        if (bottom - top < minH) {
            val add = (minH - (bottom - top)) / 2f
            top -= add; bottom += add
        }

        // 이미지 경계로 클램프
        left = left.coerceIn(0f, imgW.toFloat())
        top = top.coerceIn(0f, imgH.toFloat())
        right = right.coerceIn(0f, imgW.toFloat())
        bottom = bottom.coerceIn(0f, imgH.toFloat())
        if (right <= left) right = min(imgW.toFloat(), left + minW)
        if (bottom <= top) bottom = min(imgH.toFloat(), top + minH)

        return RectF(left, top, right, bottom)
    }

    // ✅ OCR용 업스케일러: 짧은 변이 targetShort 미만이면 비율 유지 리사이즈
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
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
    // ==========================================================================

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

            // 각 박스 처리
            dets.forEachIndexed { idx, det ->
                // ✅ OCR 전에 최소 크기 확보 + 패딩해서 크롭
                val expanded = expandRectToMinSize(
                    det.rect,
                    minW = 32f,              // ML Kit 최소
                    minH = 32f,              // ML Kit 최소
                    padFrac = 0.15f,         // 주변 컨텍스트 확보
                    imgW = rotated.width,
                    imgH = rotated.height
                )
                val rawCrop = cropBitmap(rotated, expanded)

                // ✅ 너무 작으면 업스케일해서 OCR 품질 확보
                val ocrCrop = upscaleForOcr(rawCrop, targetShort = 96, maxLong = 512)

                var role = "unknown"

                // OCR 먼저 시도
                try {
                    val inputImg = InputImage.fromBitmap(ocrCrop, 0)
                    val task = textRecognizer.process(inputImg)

                    task.addOnSuccessListener { visionText ->
                        val text = visionText.text.trim()
                        if (text.isNotEmpty()) {
                            role = text
                            android.util.Log.i(
                                "LABEL",
                                "[$idx] OCR  label='$role' rect=$expanded sizeRaw=${rawCrop.width}x${rawCrop.height} sizeOcr=${ocrCrop.width}x${ocrCrop.height}"
                            )
                        } else {
                            // ✅ OCR 결과는 성공이지만 텍스트가 비어있음 → 아이콘 분류로
                            role = roleClf.predictRole(rawCrop)
                            android.util.Log.d(
                                "LABEL",
                                "[$idx] ICON label='$role' rect=$expanded reason=ocr-empty sizeRaw=${rawCrop.width}x${rawCrop.height} sizeOcr=${ocrCrop.width}x${ocrCrop.height}"
                            )
                        }

                        // 오버레이 갱신
                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }.addOnFailureListener { e ->
                        // ✅ OCR 자체 실패(예: 너무 작음 등) → 아이콘 분류 fallback
                        role = roleClf.predictRole(rawCrop)
                        android.util.Log.w(
                            "LABEL",
                            "[$idx] ICON label='$role' rect=$expanded reason=ocr-failed: ${e.message} sizeRaw=${rawCrop.width}x${rawCrop.height} sizeOcr=${ocrCrop.width}x${ocrCrop.height}"
                        )

                        val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)
                            overlayView.submitBoxes(listOf(buttonBox))
                            overlayView.invalidate()
                        }
                    }
                } catch (e: Exception) {
                    // ✅ 예외 시 즉시 아이콘 분류
                    role = roleClf.predictRole(rawCrop)
                    android.util.Log.e(
                        "LABEL",
                        "[$idx] ICON label='$role' rect=$expanded reason=ocr-exception: ${e.message} sizeRaw=${rawCrop.width}x${rawCrop.height}",
                        e
                    )
                    val buttonBox = ButtonBox(id = idx, rect = det.rect, label = role)
                    overlayView.post {
                        overlayView.setSourceSize(rotated.width, rotated.height)
                        overlayView.submitBoxes(listOf(buttonBox))
                        overlayView.invalidate()
                    }
                }
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }
}