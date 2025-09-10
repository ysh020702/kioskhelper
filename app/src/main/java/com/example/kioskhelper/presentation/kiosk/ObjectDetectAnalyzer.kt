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

/*
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

    // OCR 엔진은 한 번 생성해서 재사용
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ======================= 트래킹/다수결(라벨 안정화) =======================
    // 간단한 IOU 기반 트래킹으로, 최근 N회 라벨에서 다수결을 취해 튀는 값을 완화
    private data class Track(var rect: RectF, val recent: ArrayDeque<String> = ArrayDeque())
    private val tracks = mutableListOf<Track>()
    private companion object {
        private const val VOTE_WINDOW = 5           // 최근 5개 라벨로 다수결
        private const val IOU_TH = 0.5f             // 같은 트랙으로 볼 최소 IOU
    }
    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = a.width()*a.height() + b.width()*b.height() - inter
        return if (union > 0f) inter/union else 0f
    }
    private fun assignTrack(r: RectF): Track {
        tracks.firstOrNull { iou(it.rect, r) >= IOU_TH }?.let { t ->
            t.rect = r
            return t
        }
        return Track(RectF(r)).also { tracks.add(it) }
    }
    private fun voteLabel(t: Track, newLabel: String): String {
        t.recent.addLast(newLabel)
        if (t.recent.size > VOTE_WINDOW) t.recent.removeFirst()
        return t.recent.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
    }
    // =======================================================================

    // ============== 게이트(너무 작거나 극단 비율 박스는 아이콘 분류 스킵) ==============
    private fun shouldIconClassify(r: RectF): Boolean {
        val w = r.width(); val h = r.height()
        val ar = if (h > 0f) w / h else 999f
        val area = w * h

        // 너무 작은 박스는 모델/ocr 모두 불안정 → 아이콘 분류 스킵
        if (area < 32f * 32f) return false
        // 극단적인 종횡비(가로/세로 막대) → 스크롤바 같은 오검출 빈번 → 스킵
        if (ar >= 3f || ar <= (1f/3f)) return false
        return true
    }
    // =======================================================================

    // ====================== OCR 보강(확대/정규화/가격 추출) ======================
    // 1) 너무 작은 박스는 약간 팽창(pad) 후 최소 크기 보장 → crop
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

    // 2) OCR 인식률을 위해 짧은 변이 targetShort 미만이면 업스케일
    private fun upscaleForOcr(src: Bitmap, targetShort: Int = 96, maxLong: Int = 512): Bitmap {
        val shortSide = min(src.width, src.height)
        val longSide = max(src.width, src.height)
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    // 3) OCR 결과 정규화(전각/혼동문자 간단 교정 등)
    private fun normalizeOcr(text: String): String {
        var t = text.replace(Regex("\n+"), "\n").trim()
        // 라틴 혼동 교정(상황에 맞게 가볍게만)
        t = t.replace('O','0').replace('o','0')
            .replace('l','1').replace('I','1')
            .replace('S','5')
        return t
    }

    // 4) 가격 패턴 추출(있으면 가격만 대표 라벨로 사용)
    private val priceRegex = Regex("""(\d{1,3}(,\d{3})+|\d+)\s*([원WwPp₩]?)""")
    private fun extractPrice(text: String): String? = priceRegex.find(text)?.value
    // =======================================================================

    // ========================= 비트맵 유틸(그대로 유지) =========================
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
    // =======================================================================

    override fun analyze(image: ImageProxy) {
        try {
            // 프레임 스로틀링(옵션)
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            android.util.Log.d(
                "ANALYZER",
                "frame rot=${image.imageInfo.rotationDegrees}, src=${image.width}x${image.height}"
            )

            // YUV → Bitmap 변환 및 회전 보정
            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = rotate(src, rot)
            if (rot != 0) src.recycle()

            android.util.Log.d(
                "ANALYZER",
                "bitmap=${rotated.width}x${rotated.height}, preview=${previewView.width}x${previewView.height}"
            )

            // 객체 탐지
            var dets = detector.detect(rotated)
            android.util.Log.d("ANALYZER", "detections=${dets.size}")
            dets = dets.sortedByDescending { it.score }.take(20)

            // 각 박스 처리
            dets.forEachIndexed { idx, det ->
                // 1) OCR을 위해 박스를 약간 확장 + 최소 크기 보장
                val expanded = expandRectToMinSize(
                    det.rect,
                    minW = 32f,
                    minH = 32f,
                    padFrac = 0.15f,
                    imgW = rotated.width,
                    imgH = rotated.height
                )

                // crop & ocr용 업스케일
                val rawCrop = cropBitmap(rotated, expanded)
                val ocrCrop = upscaleForOcr(rawCrop, targetShort = 96, maxLong = 512)

                // 2) OCR 시도 → 성공 시 정규화/가격 추출 → 트랙 다수결
                val inputImg = InputImage.fromBitmap(ocrCrop, 0)
                textRecognizer.process(inputImg)
                    .addOnSuccessListener { visionText ->
                        val txt = visionText.text.trim()
                        if (txt.isNotEmpty()) {
                            val norm = normalizeOcr(txt)
                            val label = (extractPrice(norm) ?: norm).take(30)

                            // 트래킹/다수결로 안정화
                            val track = assignTrack(det.rect)
                            val voted = voteLabel(track, label)

                            // 로그: OCR 경로
                            android.util.Log.i(
                                "LABEL",
                                "[$idx] OCR  label='${voted}' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                            )

                            // 오버레이 업데이트
                            val buttonBox = ButtonBox(id = idx, rect = det.rect, ocrLabel = voted,iconLabel=null)
                            overlayView.post {
                                overlayView.setSourceSize(rotated.width, rotated.height)
                                overlayView.submitBoxes(listOf(buttonBox))
                                overlayView.invalidate()
                            }
                        } else {
                            // OCR 결과 비어있음 → 아이콘 분류 경로
                            runIconPath(
                                idx = idx,
                                rect = det.rect,
                                cropForIcon = rawCrop,
                                expanded = expanded,
                                rotated = rotated,
                                reason = "ocr-empty",
                                ocrW = ocrCrop.width,
                                ocrH = ocrCrop.height
                            )
                        }

                        // 메모리 정리 (성공 콜백)
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
                    .addOnFailureListener { e ->
                        // OCR 자체 실패 → 아이콘 분류 경로
                        runIconPath(
                            idx = idx,
                            rect = det.rect,
                            cropForIcon = rawCrop,
                            expanded = expanded,
                            rotated = rotated,
                            reason = "ocr-failed: ${e.message}",
                            ocrW = ocrCrop.width,
                            ocrH = ocrCrop.height
                        )

                        // 메모리 정리 (실패 콜백)
                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
            }

            // 주의: OCR 콜백은 비동기라 여기서 rotated를 바로 recycle하면 안 됨.

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }

    // ====================== 아이콘 분류 경로(공통 헬퍼) =======================
    private fun runIconPath(
        idx: Int,
        rect: RectF,
        cropForIcon: Bitmap,       // 아이콘 분류 입력용 원본 crop
        expanded: RectF,           // 로그용(확장된 OCR 영역)
        rotated: Bitmap,           // 오버레이 사이즈 설정용
        reason: String,
        ocrW: Int,
        ocrH: Int
    ) {
        // 게이트: 너무 작거나 막대형은 아이콘 분류 스킵 → 라벨링 생략/유지
        if (!shouldIconClassify(rect)) {
            android.util.Log.w(
                "LABEL",
                "[$idx] SKIP label='non_clickable' rect=$expanded reason=$reason raw=${cropForIcon.width}x${cropForIcon.height} ocr=${ocrW}x${ocrH}"
            )
            return
        }

        // 아이콘 분류 수행
        val icon = roleClf.predictRole(cropForIcon)

        // 트래킹/다수결
        val track = assignTrack(rect)
        val voted = voteLabel(track, icon)

        // 로그: ICON 경로
        android.util.Log.d(
            "LABEL",
            "[$idx] ICON label='${voted}' rect=$expanded reason=$reason raw=${cropForIcon.width}x${cropForIcon.height} ocr=${ocrW}x${ocrH}"
        )

        // 오버레이 업데이트
        val buttonBox = ButtonBox(id = idx, rect = rect, ocrLabel = null, iconLabel=voted)
        overlayView.post {
            overlayView.setSourceSize(rotated.width, rotated.height)
            overlayView.submitBoxes(listOf(buttonBox))
            overlayView.invalidate()
        }
    }
}

 */
/*
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

    // ML Kit OCR (재사용)
    private val textRecognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    // ───────────── 트래킹/다수결로 라벨 흔들림 완화 ─────────────
    private data class Track(var rect: RectF, val recent: ArrayDeque<String> = ArrayDeque())
    private val tracks = mutableListOf<Track>()

    private companion object {
        private const val VOTE_WINDOW = 5
        private const val IOU_TH = 0.5f
    }

    private fun iou(a: RectF, b: RectF): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = a.width() * a.height() + b.width() * b.height() - inter
        return if (union > 0f) inter / union else 0f
    }

    private fun assignTrack(r: RectF): Track {
        tracks.firstOrNull { iou(it.rect, r) >= IOU_TH }?.let { t ->
            t.rect = r
            return t
        }
        return Track(RectF(r)).also { tracks.add(it) }
    }

    private fun voteLabel(t: Track, newLabel: String): String {
        t.recent.addLast(newLabel)
        if (t.recent.size > VOTE_WINDOW) t.recent.removeFirst()
        return t.recent.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
    }
    // ────────────────────────────────────────────────────────────────

    // ───── 게이트: 극단적/초소형 박스는 아이콘 분류 스킵 ─────
    private fun shouldIconClassify(r: RectF): Boolean {
        val w = r.width(); val h = r.height()
        val ar = if (h > 0f) w / h else 999f
        val area = w * h
        if (area < 32f * 32f) return false
        if (ar >= 3f || ar <= (1f / 3f)) return false
        return true
    }
    // ────────────────────────────────────────────────────────────────

    // ───── OCR 보강(확장/업스케일/정규화/가격추출) ─────
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
        if (shortSide >= targetShort) return src
        val scale = targetShort.toFloat() / shortSide
        val outW = (src.width * scale).toInt().coerceAtMost(maxLong)
        val outH = (src.height * scale).toInt().coerceAtMost(maxLong)
        return Bitmap.createScaledBitmap(src, outW, outH, true)
    }

    private fun normalizeOcr(text: String): String {
        var t = text.replace(Regex("\n+"), "\n").trim()
        t = t.replace('O','0').replace('o','0')
            .replace('l','1').replace('I','1')
            .replace('S','5')
        return t
    }

    private val priceRegex = Regex("""(\d{1,3}(,\d{3})+|\d+)\s*([원WwPp₩]?)""")
    private fun extractPrice(text: String): String? = priceRegex.find(text)?.value
    // ────────────────────────────────────────────────────────────────

    // ───── 비트맵 유틸 ─────
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
    // ───────────────────────

    override fun analyze(image: ImageProxy) {
        try {
            val now = System.currentTimeMillis()
            if (throttleMs > 0 && now - lastTs.get() < throttleMs) { image.close(); return }
            lastTs.set(now)

            val src = yuvConverter.toBitmap(image)
            val rot = image.imageInfo.rotationDegrees
            val rotated = rotate(src, rot)
            if (rot != 0) src.recycle()

            var dets = detector.detect(rotated)
            dets = dets.sortedByDescending { it.score }.take(20)

            // ✅ 이 프레임의 모든 버튼 박스를 모아둔다
            val allBoxes = mutableListOf<ButtonBox>()

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

                val inputImg = InputImage.fromBitmap(ocrCrop, 0)
                textRecognizer.process(inputImg)
                    .addOnSuccessListener { visionText ->
                        val txt = visionText.text.trim()
                        if (txt.isNotEmpty()) {
                            // OCR 성공 → 정규화/가격 추출 → 트랙 다수결
                            val norm = normalizeOcr(txt)
                            val label = (extractPrice(norm) ?: norm).take(30)
                            val track = assignTrack(det.rect)
                            val voted = voteLabel(track, label)

                            // 리스트에 추가
                            allBoxes.add(
                                ButtonBox(
                                    id = idx,
                                    rect = det.rect,
                                    ocrLabel = voted,
                                    iconLabel = null
                                )
                            )

                            android.util.Log.i(
                                "LABEL",
                                "[$idx] OCR  label='$voted' rect=$expanded raw=${rawCrop.width}x${rawCrop.height} ocr=${ocrCrop.width}x${ocrCrop.height}"
                            )
                        } else {
                            // OCR 비었으면 아이콘 분류로 대체
                            val icon = runIconPath(
                                idx = idx,
                                rect = det.rect,
                                cropForIcon = rawCrop,
                                expanded = expanded,
                                reason = "ocr-empty",
                                ocrW = ocrCrop.width,
                                ocrH = ocrCrop.height
                            )
                            allBoxes.add(
                                ButtonBox(
                                    id = idx,
                                    rect = det.rect,
                                    ocrLabel = null,
                                    iconLabel = icon
                                )
                            )
                        }

                        // ✅ 프레임 단위로 오버레이 갱신 + 전체 리스트 로그
                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)

                            val sb = StringBuilder("All Boxes in this frame:\n")
                            allBoxes.forEach { box ->
                                sb.append(
                                    "id=${box.id}, rect=${box.rect}, " +
                                            "ocrLabel=${box.ocrLabel}, iconLabel=${box.iconLabel}\n"
                                )
                            }
                            android.util.Log.i("ANALYZER_BOXES", sb.toString())

                            overlayView.submitBoxes(allBoxes)
                            overlayView.invalidate()
                        }

                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
                    .addOnFailureListener { e ->
                        // OCR 자체 실패 → 아이콘 분류 fallback
                        val icon = runIconPath(
                            idx = idx,
                            rect = det.rect,
                            cropForIcon = rawCrop,
                            expanded = expanded,
                            reason = "ocr-failed: ${e.message}",
                            ocrW = ocrCrop.width,
                            ocrH = ocrCrop.height
                        )
                        allBoxes.add(
                            ButtonBox(
                                id = idx,
                                rect = det.rect,
                                ocrLabel = null,
                                iconLabel = icon
                            )
                        )

                        overlayView.post {
                            overlayView.setSourceSize(rotated.width, rotated.height)

                            val sb = StringBuilder("All Boxes in this frame:\n")
                            allBoxes.forEach { box ->
                                sb.append(
                                    "id=${box.id}, rect=${box.rect}, " +
                                            "ocrLabel=${box.ocrLabel}, iconLabel=${box.iconLabel}\n"
                                )
                            }
                            android.util.Log.i("ANALYZER_BOXES", sb.toString())

                            overlayView.submitBoxes(allBoxes)
                            overlayView.invalidate()
                        }

                        if (ocrCrop !== rawCrop && !ocrCrop.isRecycled) ocrCrop.recycle()
                        if (!rawCrop.isRecycled) rawCrop.recycle()
                    }
            }

            // 주의: OCR 콜백은 비동기라 여기서 rotated 를 바로 recycle 하면 안 됨.

        } catch (e: Throwable) {
            android.util.Log.e("ANALYZER", "analyze error", e)
        } finally {
            image.close()
        }
    }

    // ───────── 아이콘 분류 경로: 라벨을 반환(String) ─────────
    private fun runIconPath(
        idx: Int,
        rect: RectF,
        cropForIcon: Bitmap,
        expanded: RectF,
        reason: String,
        ocrW: Int,
        ocrH: Int
    ): String {
        if (!shouldIconClassify(rect)) {
            android.util.Log.w(
                "LABEL",
                "[$idx] SKIP label='non_clickable' rect=$expanded reason=$reason raw=${cropForIcon.width}x${cropForIcon.height} ocr=${ocrW}x${ocrH}"
            )
            return "non_clickable"
        }

        val icon = roleClf.predictRole(cropForIcon)
        val track = assignTrack(rect)
        val voted = voteLabel(track, icon)

        android.util.Log.d(
            "LABEL",
            "[$idx] ICON label='$voted' rect=$expanded reason=$reason raw=${cropForIcon.width}x${cropForIcon.height} ocr=${ocrW}x${ocrH}"
        )
        return voted
    }
}
*/
@ActivityRetainedScoped
class ObjectDetectAnalyzer @Inject constructor(
    private val previewView: PreviewView,             // 런타임 전달
    private val overlayView: DetectionOverlayView,    // 런타임 전달
    private val detector: YoloV8TfliteInterpreter,    // Hilt 주입
    private val roleClf: IconRoleClassifier,          // Hilt 주입
    private val yuvConverter: YuvToRgbConverter,
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