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
    private val previewView: PreviewView,             // 런타임 전달
    private val overlayView: DetectionOverlayView,    // 런타임 전달
    private val detector: YoloV8TfliteInterpreter,    // Hilt 주입
    private val roleClf: IconRoleClassifier,          // Hilt 주입
    private val yuvConverter: YuvToRgbConverter,
    private val kioskViewModel: KioskViewModel,
    private val throttleMs: Long = 0L
) : ImageAnalysis.Analyzer {

    private val lastTs = java.util.concurrent.atomic.AtomicLong(0L)

    // 동시에 1프레임만 처리
    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val frameSeq = java.util.concurrent.atomic.AtomicLong(0L)

    // OCR 엔진 (재사용)
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ────────────── OCR 보강/유틸 ──────────────
    private fun expandRectToMinSize(
        r: RectF, minW: Float, minH: Float, padFrac: Float, imgW: Int, imgH: Int
    ): RectF {
        val w = r.width();
        val h = r.height()
        val padX = max(w * padFrac, 2f)
        val padY = max(h * padFrac, 2f)
        var left = r.left - padX
        var top = r.top - padY
        var right = r.right + padX
        var bottom = r.bottom + padY
        if (right - left < minW) {
            val add = (minW - (right - left)) / 2f; left -= add; right += add
        }
        if (bottom - top < minH) {
            val add = (minH - (bottom - top)) / 2f; top -= add; bottom += add
        }
        left = left.coerceIn(0f, imgW.toFloat()); top = top.coerceIn(0f, imgH.toFloat())
        right = right.coerceIn(0f, imgW.toFloat()); bottom = bottom.coerceIn(0f, imgH.toFloat())
        if (right <= left) right = min(imgW.toFloat(), left + minW)
        if (bottom <= top) bottom = min(imgH.toFloat(), top + minH)
        return RectF(left, top, right, bottom)
    }

    private fun upscaleForOcr(src: Bitmap, targetShort: Int = 96, maxLong: Int = 512): Bitmap {
        val shortSide = min(src.width, src.height)
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    private fun normalizeOcr(text: String): String {
        var t = text.replace(Regex("\n+"), " ").trim()
        t = t.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1')
            .replace('S', '5')
        return t
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

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) {
                image.close(); return
            }
            lastTs.set(now)

            // 동시에 1프레임만
            if (!inFlight.compareAndSet(false, true)) {
                image.close(); return
            }

            // 카메라 프레임 → 비트맵(+회전 보정)
            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = if (rot != 0) rotate(src, rot) else src
            if (rot != 0) src.recycle()

            // YOLO 탐지
            var dets = detector.detect(rotated)
            dets = dets.sortedByDescending { it.score }.take(20)

            val frameId = frameSeq.incrementAndGet()

            // ❶ 프라임 드로우: 라벨 없이 박스만 먼저 그리기 (소스 좌표계 그대로)
            val primeList = dets.mapIndexed { i, d ->
                ButtonBox(id = i, rect = d.rect, ocrLabel = null, iconLabel = null)
            }

            overlayView.post {
                // 오버레이가 항상 위에 오도록 (최초 1회만 실행돼도 OK)
                try {
                    overlayView.bringToFront()
                } catch (_: Throwable) {
                }
                overlayView.setSourceSize(rotated.width, rotated.height)
                overlayView.submitBoxes(primeList)
                overlayView.invalidate()
                android.util.Log.i(
                    "ANALYZER_BOXES",
                    "Prime draw ${primeList.size} boxes (labels pending)"
                )
            }

            // ❷ 라벨링: 각 박스 OCR → 실패 시 아이콘, 결과 들어올 때마다 업데이트
            if (dets.isEmpty()) {
                inFlight.set(false)
                return
            }

            // 작업 리스트(동기화)
            val working = java.util.Collections.synchronizedList(primeList.toMutableList())
            val pending = java.util.concurrent.atomic.AtomicInteger(dets.size)

            fun pushUpdate(reason: String) {
                if (frameSeq.get() != frameId) return // 오래된 프레임 무시
                overlayView.post {
                    overlayView.setSourceSize(rotated.width, rotated.height)
                    overlayView.submitBoxes(working.toList())
                    overlayView.invalidate()
                    android.util.Log.d(
                        "ANALYZER_BOXES",
                        "Update(${reason}) -> ${working.count { it.ocrLabel != null || it.iconLabel != null }}/${working.size}"
                    )
                }
            }

            dets.forEachIndexed { idx, det ->
                val expanded = expandRectToMinSize(
                    det.rect, minW = 32f, minH = 32f, padFrac = 0.15f,
                    imgW = rotated.width, imgH = rotated.height
                )
                val rawCrop = cropBitmap(rotated, expanded)
                val ocrCrop = upscaleForOcr(rawCrop, targetShort = 96, maxLong = 512)
                val inputImg = InputImage.fromBitmap(ocrCrop, 0)

                fun setIcon(reason: String) {
                    val icon = roleClf.predictRole(rawCrop)
                    android.util.Log.d(
                        "LABEL",
                        "[$idx] ICON label='$icon' reason=$reason rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                    )
                    // id는 탐지 순서로 안정적이므로 index로 교체
                    val old = working[idx]
                    working[idx] = old.copy(iconLabel = icon) // ocrLabel은 null 유지
                    pushUpdate("icon")
                }

                textRecognizer.process(inputImg)
                    .addOnSuccessListener { vt ->
                        val txt = vt.text.trim()
                        if (txt.isNotEmpty()) {
                            val label = normalizeOcr(txt).take(30)
                            android.util.Log.i(
                                "LABEL",
                                "[$idx] OCR  label='$label' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                            )
                            val old = working[idx]
                            working[idx] = old.copy(ocrLabel = label) // iconLabel은 그대로
                            pushUpdate("ocr")
                        } else {
                            setIcon("ocr-empty")
                        }
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                        if (pending.decrementAndGet() == 0) {
                            // 마지막 한 번 더 확정 로그
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        setIcon("ocr-failed: ${e.message}")
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                        if (pending.decrementAndGet() == 0) {
                            android.util.Log.i(
                                "ANALYZER_BOXES",
                                buildString {
                                    append("Final list for frame ").append(frameId).append('\n')
                                    working.forEach { b ->
                                        append("id=").append(b.id)
                                            .append(", rect=").append(b.rect)
                                            .append(", ocr=").append(b.ocrLabel)
                                            .append(", icon=").append(b.iconLabel).append('\n')
                                    }
                                }
                            )
                            kioskViewModel.setDetectedButtons(working.toList())
                            inFlight.set(false)
                        }
                    }
            }

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
            overlayView.post {
                overlayView.submitBoxes(emptyList())
                overlayView.invalidate()
            }
            inFlight.set(false)
        } finally {
            image.close()
        }
    }
}