package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.*
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

data class YoloDet(
    val cls: Int,
    val score: Float,
    val rect: RectF  // src(회전 보정된 원본) 좌표계
)

class YoloV8TfliteInterpreter(
    context: Context,
    modelAsset: String = "model.tflite",
    private val inputSize: Int = 640,
    private val confThresh: Float = 0.15f,  // ← 디버깅용으로 낮춤(필요시 0.25로 복구)
    private val iouThresh: Float = 0.45f,
    numThreads: Int = 2
) {
    private val interpreter: Interpreter
    private val inType: DataType
    private val outType: DataType
    private val outShape: IntArray

    init {
        val fd = context.assets.openFd(modelAsset)
        val channel = FileInputStream(fd.fileDescriptor).channel
        val model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        val opts = Interpreter.Options().apply { setNumThreads(numThreads) }
        interpreter = Interpreter(model, opts)

        inType = interpreter.getInputTensor(0).dataType()
        val outTensor = interpreter.getOutputTensor(0)
        outType = outTensor.dataType()
        outShape = outTensor.shape()

    }

    fun detect(src: Bitmap): List<YoloDet> {
        val prep = letterbox(src, inputSize, inputSize)
        val input = makeInputBuffer(prep.bitmap, inType)

        val outBuf: Any = when (outType) {
            DataType.FLOAT32 -> Array(outShape[0]) { Array(outShape[1]) { FloatArray(outShape[2]) } }
            DataType.UINT8   -> Array(outShape[0]) { Array(outShape[1]) { ByteArray(outShape[2]) } }
            else -> error("Unsupported output type: $outType")
        }

        interpreter.run(input, outBuf)

        val detsInputSpace: List<YoloDet> = when (outType) {
            DataType.FLOAT32 -> {
                val flatArray = (outBuf as Array<Array<FloatArray>>)[0].flatMap { it.asIterable() }.toFloatArray()
                parseFloatOutput(flatArray, outShape)
            }
            DataType.UINT8 -> {
                val flatArray = (outBuf as Array<Array<ByteArray>>)[0].flatMap { it.asIterable() }.toByteArray()
                parseUint8Output(flatArray, outShape)
            }
            else -> emptyList()
        }

        val mapped = detsInputSpace
            .map { it.copy(rect = unletterbox(it.rect, prep, src.width, src.height)) }
            .filter { it.rect.width() >= 1f && it.rect.height() >= 1f }

        // 🔽🔽🔽 “진짜 버튼만” 남기기: 버튼 특화 후처리 필터 체인 적용
        val params = DetFilterParams(
            scoreThresh = max(confThresh, 0.54f), // 디버깅용 confThresh가 낮아도 최소 0.35는 유지
            minRelArea = 0.0012f,
            maxRelArea = 0.60f,
            minAspect = 0.4f,
            maxAspect = 2.5f,
            nmsIou = iouThresh,
            keepTopK = 50,
            // allowClasses = setOf( /* 버튼/아이콘 클래스 id */ ),
            // classThresh = mapOf( /* clsId to thresh */ )
        )
        val filtered = filterForButtons(mapped, src.width, src.height, params)

        Log.d("YOLO", "afterFilterTop=" + filtered.sortedByDescending { it.score }
            .joinToString { "s=%.3f rect=%s\n".format(it.score, it.rect.toShortString()) })

        return filtered
    }

    // ───────────── 전처리/후처리 유틸 ─────────────

    private data class Letterbox(val bitmap: Bitmap, val gain: Float, val padX: Float, val padY: Float)

    private fun letterbox(src: Bitmap, dstW: Int, dstH: Int): Letterbox {
        val r = min(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val nw = (src.width * r).toInt()
        val nh = (src.height * r).toInt()
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val dx = (dstW - nw) / 2f
        val dy = (dstH - nh) / 2f
        c.drawBitmap(resized, dx, dy, null)
        return Letterbox(out, gain = r, padX = dx, padY = dy)
    }

    private fun unletterbox(r: RectF, lb: Letterbox, srcW: Int, srcH: Int): RectF {
        val nx1 = r.left.coerceIn(0f, 1f)
        val ny1 = r.top.coerceIn(0f, 1f)
        val nx2 = r.right.coerceIn(0f, 1f)
        val ny2 = r.bottom.coerceIn(0f, 1f)

        var x1 = ((nx1 * lb.bitmap.width  - lb.padX) / lb.gain)
        var y1 = ((ny1 * lb.bitmap.height - lb.padY) / lb.gain)
        var x2 = ((nx2 * lb.bitmap.width  - lb.padX) / lb.gain)
        var y2 = ((ny2 * lb.bitmap.height - lb.padY) / lb.gain)

        if (x2 < x1) { val t = x1; x1 = x2; x2 = t }
        if (y2 < y1) { val t = y1; y1 = y2; y2 = t }

        x1 = x1.coerceIn(0f, srcW.toFloat())
        y1 = y1.coerceIn(0f, srcH.toFloat())
        x2 = x2.coerceIn(0f, srcW.toFloat())
        y2 = y2.coerceIn(0f, srcH.toFloat())

        if (x2 - x1 < 1f) x2 = (x1 + 1f).coerceAtMost(srcW - 1f)
        if (y2 - y1 < 1f) y2 = (y1 + 1f).coerceAtMost(srcH - 1f)

        return RectF(x1, y1, x2, y2)
    }

    private fun makeInputBuffer(bmp: Bitmap, type: DataType): ByteBuffer {
        val w = bmp.width; val h = bmp.height
        return if (type == DataType.FLOAT32) {
            val buf = ByteBuffer.allocateDirect((4L * w * h * 3).toInt()).order(ByteOrder.nativeOrder())
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            var i = 0
            while (i < px.size) {
                val p = px[i++]
                val r = ((p ushr 16) and 0xFF) / 255f
                val g = ((p ushr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                buf.putFloat(r); buf.putFloat(g); buf.putFloat(b)
            }
            buf.rewind(); buf
        } else {
            val buf = ByteBuffer.allocateDirect((1L * w * h * 3).toInt()).order(ByteOrder.nativeOrder())
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            var i = 0
            while (i < px.size) {
                val p = px[i++]
                buf.put(((p ushr 16) and 0xFF).toByte())
                buf.put(((p ushr 8) and 0xFF).toByte())
                buf.put((p and 0xFF).toByte())
            }
            buf.rewind(); buf
        }
    }

    private fun isNormalized(values: FloatArray, sample: Int = 200): Boolean {
        val n = values.size
        val limit = min(n, sample)
        var cntIn01 = 0
        for (i in 0 until limit) {
            val v = values[i].coerceIn(-1e6f, 1e6f)
            if (v in 0f..1f) cntIn01++
        }
        return cntIn01 >= (limit * 0.8f)
    }

    private fun parseFloatOutput(out: FloatArray, shape: IntArray): List<YoloDet> {
        require(shape.size == 3 && shape[0] == 1) { "Unexpected output shape: ${shape.contentToString()}" }

        // 2D 평면의 row/col로 해석
        val rows = shape[1]
        val cols = shape[2]
        val C = min(rows, cols)   // 채널 수(보통 5/84/85)
        val N = max(rows, cols)   // 박스 수(보통 8400)
        val isCN = (rows == C)    // [1, C, N]이면 true, [1, N, C]이면 false

        fun get(i: Int, ci: Int): Float {
            // i: [0..N-1], ci: [0..C-1]
            return if (isCN) {
                // row=ci, col=i
                out[ci * cols + i]
            } else {
                // row=i, col=ci
                out[i * cols + ci]
            }
        }

        Log.d("YOLO", "layout=${if (isCN) "[1,C,N]" else "[1,N,C]"} C=$C N=$N")

        // 좌표 스케일 판별
        val cxList = FloatArray(N) { get(it, 0) }
        val cyList = FloatArray(N) { get(it, 1) }
        val wList  = FloatArray(N) { get(it, 2) }
        val hList  = FloatArray(N) { get(it, 3) }
        val normalized = isNormalized(cxList) && isNormalized(cyList)

        fun toRectN01(cx: Float, cy: Float, w: Float, h: Float): RectF {
            return if (normalized) {
                RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            } else {
                RectF(
                    (cx - w / 2f) / inputSize,
                    (cy - h / 2f) / inputSize,
                    (cx + w / 2f) / inputSize,
                    (cy + h / 2f) / inputSize
                )
            }
        }

        dumpRange(::get, N, tag = "head", take = 8)

        val list = ArrayList<YoloDet>(128)

        when (C) {
            5 -> { // [cx,cy,w,h,score]
                for (i in 0 until N) {
                    val cx = get(i, 0)
                    val cy = get(i, 1)
                    val w  = get(i, 2)
                    val h  = get(i, 3)
                    val s  = sigmoid(get(i, 4))
                    if (s >= confThresh) {
                        val r = toRectN01(cx, cy, w, h)
                        if (r.width() > 1e-6f && r.height() > 1e-6f) {
                            list += YoloDet(cls = 0, score = s, rect = r)
                        }
                    }
                }
            }
            84 -> { // [cx,cy,w,h, 80class]
                for (i in 0 until N) {
                    val cx = get(i, 0)
                    val cy = get(i, 1)
                    val w  = get(i, 2)
                    val h  = get(i, 3)
                    var best = -Float.MAX_VALUE
                    var bestIdx = -1
                    for (ci in 4 until 84) {
                        val v = asProb(get(i, ci))
                        if (v > best) { best = v; bestIdx = ci - 4 }
                    }
                    if (best >= confThresh) {
                        val r = toRectN01(cx, cy, w, h)
                        if (r.width() > 1e-6f && r.height() > 1e-6f) {
                            list += YoloDet(bestIdx, best, r)
                        }
                    }
                }
            }
            85 -> { // [cx,cy,w,h,obj, 80class]
                for (i in 0 until N) {
                    val cx = get(i, 0)
                    val cy = get(i, 1)
                    val w  = get(i, 2)
                    val h  = get(i, 3)
                    val obj = sigmoid(get(i, 4))
                    var best = -Float.MAX_VALUE
                    var bestIdx = -1
                    for (ci in 5 until 85) {
                        val v = asProb(get(i, ci))
                        if (v > best) { best = v; bestIdx = ci - 5 }
                    }
                    val s = obj * best
                    if (s >= confThresh) {
                        val r = toRectN01(cx, cy, w, h)
                        if (r.width() > 1e-6f && r.height() > 1e-6f) {
                            list += YoloDet(bestIdx, s, r)
                        }
                    }
                }
            }
            else -> {
                Log.w("YOLO", "Unhandled channel count C=$C; customize parser.")
            }
        }

        return nms(list, iouThresh)
    }

    private fun parseUint8Output(@Suppress("UNUSED_PARAMETER") out: ByteArray, @Suppress("UNUSED_PARAMETER") shape: IntArray): List<YoloDet> {
        Log.w("YOLO", "UINT8 output parsing not implemented.")
        return emptyList()
    }

    private fun nms(boxes: List<YoloDet>, iouTh: Float): List<YoloDet> {
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val kept = mutableListOf<YoloDet>()
        while (sorted.isNotEmpty()) {
            val a = sorted.removeAt(0)
            kept += a
            val it = sorted.iterator()
            while (it.hasNext()) {
                val b = it.next()
                if (a.cls == b.cls && iou(a.rect, b.rect) > iouTh) it.remove()
            }
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val ix = maxOf(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = maxOf(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        if (inter <= 0f) return 0f
        val ua = a.width() * a.height() + b.width() * b.height() - inter
        return if (ua > 0f) inter / ua else 0f
    }

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x)))

    private fun looksProb(v: Float) = v in -0.001f..1.001f   // 느슨한 체크
    private fun asProb(x: Float): Float = if (looksProb(x)) x else sigmoid(x)

    private fun dumpRange(get: (Int, Int) -> Float, n: Int, tag: String, take: Int = 10) {
        val t = min(n, take)
        val xs = FloatArray(t) { get(it, 0) }
        val ys = FloatArray(t) { get(it, 1) }
        val ws = FloatArray(t) { get(it, 2) }
        val hs = FloatArray(t) { get(it, 3) }
        Log.d("YOLO", "$tag cx=${xs.joinToString(limit = t)}")
        Log.d("YOLO", "$tag cy=${ys.joinToString(limit = t)}")
        Log.d("YOLO", "$tag w =${ws.joinToString(limit = t)}")
        Log.d("YOLO", "$tag h =${hs.joinToString(limit = t)}")
    }

    //------------필터링 유틸-------
    private fun filterForButtons(
        raw: List<YoloDet>, imgW: Int, imgH: Int, p: DetFilterParams
    ): List<YoloDet> {
        if (raw.isEmpty()) return emptyList()
        val frameArea = imgW.toFloat() * imgH.toFloat()
        val minArea = frameArea * p.minRelArea
        val maxArea = frameArea * p.maxRelArea

        // 1) 크기/비율/경계 + (옵션)클래스/점수 컷
        val f1 = raw.filter { d ->
            val r = d.rect
            val w = r.width(); val h = r.height()
            val area = w * h
            if (area < minArea || area > maxArea) return@filter false

            val asp = if (h > 0f) w / h else 999f
            if (asp < p.minAspect || asp > p.maxAspect) return@filter false

            if (r.left <= p.borderPx || r.top <= p.borderPx ||
                r.right >= imgW - p.borderPx || r.bottom >= imgH - p.borderPx) {
                // 경계에 걸친 작은 박스는 더 높은 신뢰도 요구
                if (d.score < (p.scoreThresh + 0.1f)) return@filter false
            }

            if (p.allowClasses != null && d.cls !in p.allowClasses) return@filter false
            val thr = p.classThresh[d.cls] ?: p.scoreThresh
            d.score >= thr
        }
        if (f1.isEmpty()) return emptyList()

        // 2) NMS
        val sorted = f1.sortedByDescending { it.score }
        val kept = ArrayList<YoloDet>(sorted.size)
        for (c in sorted) {
            var ok = true
            for (k in kept) {
                if (iou(c.rect, k.rect) >= p.nmsIou) { ok = false; break }
            }
            if (ok) kept.add(c)
        }

        // 3) Top-K
        return kept.take(p.keepTopK)
    }
}
