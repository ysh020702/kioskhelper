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

/**
 * YOLOv8 TFLite용 초간단 해석기
 * - 입력: [1, input, input, 3] float32/uint8
 * - 출력: 흔히 [1,84,8400] (YOLOv8) 또는 [1,8400,85] (v5형식)
 *   - C==84 → 4(box) + 80(class), obj 없음 (v8 스타일)
 *   - C==85 → 4 + 1(obj) + 80(class) (v5 스타일)
 */
class YoloV8TfliteInterpreter(
    context: Context,
    modelAsset: String = "model.tflite",
    private val inputSize: Int = 640,     // 네 모델 입력 크기(보통 640 또는 320)
    private val confThresh: Float = 0.25f,
    private val iouThresh: Float = 0.45f,
    numThreads: Int = 2
) {
    private val interpreter: Interpreter
    private val inType: DataType
    private val outType: DataType
    private val outShape: IntArray

    init {
        // 모델 로드
        val fd = context.assets.openFd(modelAsset)
        val channel = FileInputStream(fd.fileDescriptor).channel
        val model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        val opts = Interpreter.Options().apply { setNumThreads(numThreads) }
        interpreter = Interpreter(model, opts)

        inType = interpreter.getInputTensor(0).dataType()
        val outTensor = interpreter.getOutputTensor(0)
        outType = outTensor.dataType()
        outShape = outTensor.shape()

        Log.d("YOLO", "IN  type=$inType, shape=${interpreter.getInputTensor(0).shape().contentToString()}")
        Log.d("YOLO", "OUT type=$outType, shape=${outShape.contentToString()}")
    }

    /**
     * src: Analyzer에서 회전 보정된 Bitmap (예: ImageProxy 회전 반영 후)
     * return: src 좌표계의 박스들
     */
    fun detect(src: Bitmap): List<YoloDet> {
        // 1) letterbox 전처리 (gain/pad 기록)
        val prep = letterbox(src, inputSize, inputSize)

        // 2) 입력 버퍼 준비
        val input = makeInputBuffer(prep.bitmap, inType)

        // 3) 출력 버퍼 준비 (단일 출력 텐서 가정)
        // outShape는 [1, 5, 8400]
        val outBuf: Any = when (outType) {
            DataType.FLOAT32 -> Array(outShape[0]) {
                Array(outShape[1]) {
                    FloatArray(outShape[2])
                }
            }
            DataType.UINT8 -> Array(outShape[0]) {
                Array(outShape[1]) {
                    ByteArray(outShape[2])
                }
            }
            else -> error("Unsupported output type: $outType")
        }

        // 4) 추론
        interpreter.run(input, outBuf)

        // 5) 출력 해석
        val detsInputSpace: List<YoloDet> = when (outType) {
            DataType.FLOAT32 -> {
                // 3차원 배열을 1차원 배열로 변환
                val flatArray = (outBuf as Array<Array<FloatArray>>)[0].flatMap { it.asIterable() }.toFloatArray()
                parseFloatOutput(flatArray, outShape)
            }
            DataType.UINT8 -> {
                // 3차원 배열을 1차원 배열로 변환
                val flatArray = (outBuf as Array<Array<ByteArray>>)[0].flatMap { it.asIterable() }.toByteArray()
                parseUint8Output(flatArray, outShape)
            }
            else -> emptyList()
        }

        val top = detsInputSpace.sortedByDescending { it.score }
        Log.d("YOLO", "tops=" + top.joinToString { "%.2f".format(it.score) })

        // 6) letterbox 역변환 → src 좌표
        return detsInputSpace.map { it.copy(rect = unletterbox(it.rect, prep, src.width, src.height)) }
    }

    // ───────────── 전처리/후처리 유틸 ─────────────

    private data class Letterbox(val bitmap: Bitmap, val gain: Float, val padX: Float, val padY: Float)

    private fun letterbox(src: Bitmap, dstW: Int, dstH: Int): Letterbox {
        val r = min(dstW.toFloat()/src.width, dstH.toFloat()/src.height) // FIT
        val nw = (src.width * r).toInt()
        val nh = (src.height * r).toInt()
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)  // 패딩 색 (훈련과 맞추면 가장 좋음)
        val dx = (dstW - nw) / 2f
        val dy = (dstH - nh) / 2f
        c.drawBitmap(resized, dx, dy, null)
        return Letterbox(out, gain = r, padX = dx, padY = dy)
    }

    private fun unletterbox(r: RectF, lb: Letterbox, srcW: Int, srcH: Int): RectF {
        val x1 = ((r.left   - lb.padX) / lb.gain).coerceIn(0f, srcW.toFloat())
        val y1 = ((r.top    - lb.padY) / lb.gain).coerceIn(0f, srcH.toFloat())
        val x2 = ((r.right  - lb.padX) / lb.gain).coerceIn(0f, srcW.toFloat())
        val y2 = ((r.bottom - lb.padY) / lb.gain).coerceIn(0f, srcH.toFloat())
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
                buf.putFloat(r); buf.putFloat(g); buf.putFloat(b) // RGB
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

    private fun parseFloatOutput(out: FloatArray, shape: IntArray): List<YoloDet> {
        require(shape.size == 3 && shape[0] == 1) { "Unexpected output shape: ${shape.contentToString()}" }

        // [1, N, C] or [1, C, N]
        val n: Int
        val c: Int
        val nFirst: Boolean
        if (shape[1] >= shape[2]) { // [1, N, C]
            n = shape[1]; c = shape[2]; nFirst = true
        } else {                    // [1, C, N]  ← 너의 경우 [1, 5, 8400]
            c = shape[1]; n = shape[2]; nFirst = false
        }

        fun get(i: Int, ci: Int): Float {
            // i: 0..n-1, ci: 0..c-1
            return if (nFirst) out[i * c + ci] else out[ci * n + i]
        }

        val list = ArrayList<YoloDet>(128)

        when (c) {
            5 -> {
                // 단일 클래스: [x, y, w, h, score]
                for (i in 0 until n) {
                    val x = get(i, 0)
                    val y = get(i, 1)
                    val w = get(i, 2)
                    val h = get(i, 3)
                    val s = sigmoid(get(i, 4)) // 보통 로짓 → 시그모이드, 이미 0~1이면 필요 없음

                    if (s >= confThresh) {
                        val rect = RectF(x - w/2f, y - h/2f, x + w/2f, y + h/2f)
                        list += YoloDet(cls = 0, score = s, rect = rect) // 단일 클래스라 0으로 고정
                    }
                }
            }

            84 -> {
                // v8 멀티클래스(4+80)
                for (i in 0 until n) {
                    val x = get(i, 0); val y = get(i, 1); val w = get(i, 2); val h = get(i, 3)
                    var best = -Float.MAX_VALUE; var bestIdx = -1
                    var ci = 4
                    while (ci < 84) {
                        val v = sigmoid(get(i, ci))
                        if (v > best) { best = v; bestIdx = ci - 4 }
                        ci++
                    }
                    if (best >= confThresh) {
                        val rect = RectF(x - w/2f, y - h/2f, x + w/2f, y + h/2f)
                        list += YoloDet(bestIdx, best, rect)
                    }
                }
            }

            85 -> {
                // v5 형식(4 + obj + 80)
                for (i in 0 until n) {
                    val x = get(i, 0); val y = get(i, 1); val w = get(i, 2); val h = get(i, 3)
                    val obj = sigmoid(get(i, 4))
                    var best = -Float.MAX_VALUE; var bestIdx = -1
                    var ci = 5
                    while (ci < 85) {
                        val v = sigmoid(get(i, ci))
                        if (v > best) { best = v; bestIdx = ci - 5 }
                        ci++
                    }
                    val s = obj * best
                    if (s >= confThresh) {
                        val rect = RectF(x - w/2f, y - h/2f, x + w/2f, y + h/2f)
                        list += YoloDet(bestIdx, s, rect)
                    }
                }
            }

            else -> {
                Log.w("YOLO", "Unhandled channel count c=$c; customize parser.")
            }
        }

        return nms(list, iouThresh)
    }

    private fun parseUint8Output(out: ByteArray, shape: IntArray): List<YoloDet> {
        // 필요 시 양자화 파라미터로 복원(여기서는 생략)
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
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val uni = a.width() * a.height() + b.width() * b.height() - inter
        return if (uni <= 0f) 0f else inter / uni
    }

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x)))
}