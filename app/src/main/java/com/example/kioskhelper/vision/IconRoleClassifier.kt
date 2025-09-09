package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.kioskhelper.core.Utils
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "IconRoleClassifier"

class IconRoleClassifier(ctx: Context, modelAsset: String) {

    private val roles = arrayOf(
        "add", "arrow_down", "arrow_left", "arrow_right", "arrow_up",
        "barcode", "brightness", "close",
        "delete", "home", "menu", "minus", "qr_code", "scroll_bar"
    )
    private val interpreter: Interpreter = Interpreter(Utils.loadModel(ctx, modelAsset))

    init {
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        val inShape = inTensor.shape()   // ex) [1, 640, 640, 3]
        val outShape = outTensor.shape() // ex) [1, 8]  or  [1, 5, 8400]

        Log.d(TAG, "IN  shape=${inShape.contentToString()}, type=${inTensor.dataType()}")
        Log.d(TAG, "OUT shape=${outShape.contentToString()}, type=${outTensor.dataType()}")

    }


    fun predictRole(crop: Bitmap): String {
        // ── 0) 스펙 읽기 ─────────────────────────────────────────────
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        val inShape = inTensor.shape()   // [1,H,W,3] or [1,3,H,W]
        val outShape = outTensor.shape() // [1, numLogits]
        val inType = inTensor.dataType()
        val outType = outTensor.dataType()

        val isNHWC = (inShape.size == 4 && inShape[3] == 3)
        val isNCHW = (inShape.size == 4 && inShape[1] == 3)
        require(isNHWC || isNCHW) { "Unsupported input shape: ${inShape.contentToString()}" }

        val inH = if (isNHWC) inShape[1] else inShape[2]
        val inW = if (isNHWC) inShape[2] else inShape[3]

        require(outShape.size == 2 && outShape[0] == 1) {
            "Unexpected output shape: ${outShape.contentToString()}"
        }
        val numLogits = outShape[1]
        if (numLogits != roles.size) {
            Log.w(TAG, "roles.size=${roles.size} != logits=$numLogits; overflow는 'unknown' 처리")
        }

        // ── 1) 레터박스 리사이즈 ─────────────────────────────────────
        fun letterboxToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
            val r = kotlin.math.min(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
            val nw = (src.width * r).toInt().coerceAtLeast(1)
            val nh = (src.height * r).toInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
            val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(out)
            c.drawColor(android.graphics.Color.BLACK)
            val dx = (dstW - nw) / 2f
            val dy = (dstH - nh) / 2f
            c.drawBitmap(resized, dx, dy, null)
            return out
        }

        val x = letterboxToSize(crop, inW, inH)

        // ── 2) 입력 버퍼 작성 (FLOAT32/UINT8 × NHWC/NCHW 모두 대응) ──  (수정함)
        val input: Any = when (inType) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val buf = ByteBuffer.allocateDirect(4 * inW * inH * 3)
                    .order(ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)
                fun norm(v: Float) = (v - 0.5f) / 0.5f // 너가 쓰던 정규화 유지

                if (isNHWC) {
                    var i = 0
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[i++]
                        val r = ((p ushr 16) and 0xFF) / 255f
                        val g = ((p ushr 8) and 0xFF) / 255f
                        val b = (p and 0xFF) / 255f
                        buf.putFloat(norm(r)); buf.putFloat(norm(g)); buf.putFloat(norm(b))
                    }
                } else { // NCHW: [C,H,W]
                    for (c in 0..2) for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]
                        val r = ((p ushr 16) and 0xFF) / 255f
                        val g = ((p ushr 8) and 0xFF) / 255f
                        val b = (p and 0xFF) / 255f
                        val v = when (c) {
                            0 -> r; 1 -> g; else -> b
                        }
                        buf.putFloat(norm(v))
                    }
                }
                buf.rewind(); buf
            }

            org.tensorflow.lite.DataType.UINT8 -> {
                val buf = ByteBuffer.allocateDirect(inW * inH * 3)
                    .order(ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)

                if (isNHWC) {
                    var i = 0
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[i++]
                        buf.put(((p ushr 16) and 0xFF).toByte())
                        buf.put(((p ushr 8) and 0xFF).toByte())
                        buf.put((p and 0xFF).toByte())
                    }
                } else { // NCHW
                    for (c in 0..2) for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]
                        val v = when (c) {
                            0 -> (p ushr 16) and 0xFF
                            1 -> (p ushr 8) and 0xFF
                            else -> p and 0xFF
                        }
                        buf.put(v.toByte())
                    }
                }
                buf.rewind(); buf
            }

            else -> error("Unsupported input dtype: $inType")
        }

        // ── 3) 출력 버퍼 준비 ────────────────────────────────────────
        val outBuf: Any = when (outType) {
            org.tensorflow.lite.DataType.FLOAT32 -> Array(1) { FloatArray(numLogits) }
            org.tensorflow.lite.DataType.UINT8 -> Array(1) { ByteArray(numLogits) }
            else -> error("Unsupported output dtype: $outType")
        }

        // ── 4) 추론 ──────────────────────────────────────────────────
        interpreter.run(input, outBuf)

        // ── 5) argmax ────────────────────────────────────────────────
        val scores: FloatArray = when (outType) {
            org.tensorflow.lite.DataType.FLOAT32 -> (outBuf as Array<FloatArray>)[0]
            org.tensorflow.lite.DataType.UINT8 -> {
                val arr = (outBuf as Array<ByteArray>)[0]
                FloatArray(arr.size) { (arr[it].toInt() and 0xFF) / 255f }
            }

            else -> error("Unsupported output dtype: $outType")
        }

        var bi = 0
        for (i in 1 until scores.size) if (scores[i] > scores[bi]) bi = i

        return if (bi in roles.indices) roles[bi]
        else roles.getOrElse(bi.coerceAtMost(roles.lastIndex)) { "unknown" }
    }
}
