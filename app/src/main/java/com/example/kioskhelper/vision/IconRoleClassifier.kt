package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.example.kioskhelper.core.Utils
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

private const val TAG = "IconRoleClassifier"

class IconRoleClassifier(ctx: Context, modelAsset: String) {

    private val roles = arrayOf(
        "add", "arrow_down", "arrow_left", "arrow_right", "arrow_up",
        "barcode", "brightness", "close", "delete", "home",
        "menu", "minus", "qr_code", "scroll_bar"
    )

    private val interpreter: Interpreter = Interpreter(Utils.loadModel(ctx, modelAsset))

    // ── Cached I/O spec ─────────────────────────────────────────────
    private val inShape: IntArray
    private val outShape: IntArray
    private val inType: DataType
    private val outType: DataType
    private val isNCHW: Boolean
    private val inC: Int
    private val inH: Int
    private val inW: Int

    init {
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        inShape = inTensor.shape()     // [1,H,W,3] or [1,3,H,W]
        outShape = outTensor.shape()   // [1, numClasses]
        inType = inTensor.dataType()
        outType = outTensor.dataType()

        val looksNHWC = (inShape.size == 4 && inShape[3] == 3)
        val looksNCHW = (inShape.size == 4 && inShape[1] == 3)
        isNCHW = when {
            looksNHWC -> false
            looksNCHW -> true
            else -> false // 안전 기본값(NHWC)
        }

        if (isNCHW) {
            inC = inShape[1]; inH = inShape[2]; inW = inShape[3]   // [N,C,H,W]
        } else {
            inH = inShape[1]; inW = inShape[2]; inC = inShape[3]   // [N,H,W,C]
        }

        require(inShape.size == 4 && inC == 3) {
            "Expected 3-channel input, got inShape=${inShape.contentToString()}"
        }

        // 출력은 [1, numClasses] 고정
        require(outShape.size == 2 && outShape[0] == 1) {
            "Unexpected output shape: ${outShape.contentToString()} (want [1, ${roles.size}])"
        }
        require(outShape[1] == roles.size) {
            "Label count(${roles.size}) != model out dim(${outShape[1]}). Fix roles[] or the model."
        }

        // 입력 dtype은 FLOAT32/UINT8 둘 다 허용
        require(inType == DataType.FLOAT32 || inType == DataType.UINT8) {
            "Unsupported input dtype: $inType (only FLOAT32/UINT8)"
        }
        // 출력도 FLOAT32/UINT8 둘 다 허용
        require(outType == DataType.FLOAT32 || outType == DataType.UINT8) {
            "Unsupported output dtype: $outType (only FLOAT32/UINT8)"
        }

        Log.d(TAG, "INIT inShape=${inShape.contentToString()}, inType=$inType, layout=${if (isNCHW) "NCHW" else "NHWC"}")
        Log.d(TAG, "INIT outShape=${outShape.contentToString()}, outType=$outType")
    }

    fun predictRole(crop: Bitmap): String {
        Log.d(TAG, "CALL crop=${crop.width}x${crop.height}, in=${inW}x${inH}, layout=${if (isNCHW) "NCHW" else "NHWC"}, inType=$inType")

        // 1) 레터박스 리사이즈 → (inW x inH)
        val x = letterboxToSize(crop, inW, inH)
        Log.d(TAG, "RESZ resized=${x.width}x${x.height}")

        // 2) 입력 버퍼 작성 (FLOAT32/UINT8 × NHWC/NCHW)
        val input: Any = when (inType) {
            DataType.FLOAT32 -> {
                val buf = ByteBuffer.allocateDirect(4 * inC * inH * inW).order(ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)
                fun nf(v: Float) = (v - 0.5f) / 0.5f  // [-1,1] 정규화

                if (isNCHW) {
                    // CHW 평면 순회
                    // R
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.putFloat(nf(((p ushr 16) and 0xFF) / 255f))
                    }
                    // G
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.putFloat(nf(((p ushr 8) and 0xFF) / 255f))
                    }
                    // B
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.putFloat(nf((p and 0xFF) / 255f))
                    }
                } else {
                    // NHWC 픽셀 단위 RGB
                    var i = 0
                    while (i < px.size) {
                        val p = px[i++]
                        buf.putFloat(nf(((p ushr 16) and 0xFF) / 255f))
                        buf.putFloat(nf(((p ushr 8) and 0xFF) / 255f))
                        buf.putFloat(nf(((p) and 0xFF) / 255f))
                    }
                }
                buf.rewind(); buf
            }

            DataType.UINT8 -> {
                val buf = ByteBuffer.allocateDirect(inC * inH * inW).order(ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)

                if (isNCHW) {
                    // CHW
                    // R
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.put(((p ushr 16) and 0xFF).toByte())
                    }
                    // G
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.put(((p ushr 8) and 0xFF).toByte())
                    }
                    // B
                    for (y in 0 until inH) for (xx in 0 until inW) {
                        val p = px[y * inW + xx]; buf.put(((p) and 0xFF).toByte())
                    }
                } else {
                    // NHWC
                    var i = 0
                    while (i < px.size) {
                        val p = px[i++]
                        buf.put(((p ushr 16) and 0xFF).toByte())
                        buf.put(((p ushr 8) and 0xFF).toByte())
                        buf.put(((p) and 0xFF).toByte())
                    }
                }
                buf.rewind(); buf
            }

            else -> error("Unsupported input dtype: $inType")
        }
        Log.d(TAG, "BUF  inputCapacity=${(input as ByteBuffer).capacity()} bytes (expect ${if (inType==DataType.FLOAT32) 4 else 1} * $inC * $inH * $inW)")

        // 3) 출력 버퍼 준비
        val outBuf: Any = when (outType) {
            DataType.FLOAT32 -> Array(1) { FloatArray(outShape[1]) }
            DataType.UINT8   -> Array(1) { ByteArray(outShape[1]) }
            else -> error("Unsupported output dtype: $outType")
        }

        // 4) 추론
        val t0 = System.nanoTime()
        interpreter.run(input, outBuf)
        val ms = (System.nanoTime() - t0) / 1e6
        Log.d(TAG, "RUN  tflite ${"%.2f".format(ms)} ms")

        // 5) argmax
        val scores: FloatArray = when (outType) {
            DataType.FLOAT32 -> (outBuf as Array<FloatArray>)[0]
            DataType.UINT8   -> {
                val b = (outBuf as Array<ByteArray>)[0]
                FloatArray(b.size) { (b[it].toInt() and 0xFF) / 255f }
            }
            else -> error("Unsupported output dtype: $outType")
        }

        var bi = 0
        for (j in 1 until scores.size) if (scores[j] > scores[bi]) bi = j
        val role = roles.getOrElse(bi) { "unknown" }
        Log.d(TAG, "OUT  topIdx=$bi role=$role score=${"%.3f".format(scores[bi])}")

        return role
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private fun letterboxToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val r = min(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val nw = (src.width * r).toInt().coerceAtLeast(1)
        val nh = (src.height * r).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(src, nw, nh, true)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.BLACK)
        val dx = (dstW - nw) / 2f
        val dy = (dstH - nh) / 2f
        c.drawBitmap(resized, dx, dy, null)
        return out
    }
}
