package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.kioskhelper.core.Utils
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder


class IconRoleClassifier(ctx: Context, modelAsset: String) {

    private val TAG = "RoleClf"

    private val roles = arrayOf(
        "add", "arrow_down", "arrow_left", "arrow_right", "arrow_up",
        "barcode", "brightness", "close", "delete", "home",
        "menu", "minus", "qr_code", "scroll_bar"
    )

    private val interpreter: Interpreter = Interpreter(Utils.loadModel(ctx, modelAsset))

    // FIX: 모델 입/출력 스펙을 캐시하고 레이아웃(NCHW/NHWC) 판별
    private val inShape: IntArray
    private val outShape: IntArray
    private val isNCHW: Boolean
    private val inC: Int
    private val inH: Int
    private val inW: Int

    init {
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        inShape = inTensor.shape()
        outShape = outTensor.shape()

        // ✅ FIX 1: 레이아웃 판별을 더 명확하게
        //    - NHWC: [1, H, W, 3]
        //    - NCHW: [1, 3, H, W]
        val looksNHWC = (inShape.size == 4 && inShape[3] == 3)
        val looksNCHW = (inShape.size == 4 && inShape[1] == 3)
        isNCHW = when {
            looksNHWC -> false
            looksNCHW -> true
            else -> false // 안전 기본값(NHWC)
        }

        if (isNCHW) {
            inC = inShape[1]; inH = inShape[2]; inW = inShape[3]   // [N, C, H, W]
        } else {
            inH = inShape[1]; inW = inShape[2]; inC = inShape[3]   // [N, H, W, C]
        }

        Log.d(TAG, "INIT  inShape=${inShape.contentToString()}, inType=${inTensor.dataType()}, layout=${if (isNCHW) "NCHW" else "NHWC"}")
        Log.d(TAG, "INIT outShape=${outShape.contentToString()}, outType=${outTensor.dataType()}")

        // ✅ FIX 2: 출력 차원 검증 (out= [1, numClasses])
        require(outShape.size == 2 && outShape[0] == 1) {
            "Unexpected output shape: ${outShape.contentToString()} (want [1, ${roles.size}])"
        }
        require(outShape[1] == roles.size) {
            "Label count(${roles.size}) != model out dim(${outShape[1]}). Update 'roles' or export the model again."
        }

        // ✅ (선택) 입력 dtype을 명시적으로 요구 — 이번 모델은 FLOAT32 기준
        require(inTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
            "This classifier expects FLOAT32 input. Got ${inTensor.dataType()}."
        }
        require(inC == 3) { "Expected 3-channel RGB input, but got C=$inC, inShape=${inShape.contentToString()}" }
    }

    fun predictRole(crop: Bitmap): String {
        val inType  = interpreter.getInputTensor(0).dataType() // FLOAT32 예상
        Log.d(TAG, "CALL  crop=${crop.width}x${crop.height}, inH=$inH, inW=$inW, inC=$inC, layout=${if (isNCHW) "NCHW" else "NHWC"}, inType=$inType")

        // 레터박스 리사이즈 → (inW x inH)
        fun letterboxToSize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
            val r = kotlin.math.min(dstW.toFloat()/src.width, dstH.toFloat()/src.height)
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
        Log.d(TAG, "RESZ  resized=${x.width}x${x.height}")

        // 입력 버퍼 (FLOAT32): 정확한 용량 = 4 * C * H * W
        val input: ByteBuffer = ByteBuffer.allocateDirect(4 * inC * inH * inW).order(ByteOrder.nativeOrder())

        // 픽셀 읽기(HWC)
        val pixels = IntArray(inW * inH)
        x.getPixels(pixels, 0, inW, 0, 0, inW, inH)

        // 로컬 함수(일반 함수이므로 OK — inline 아님)
        fun norm(v: Int): Float = ((v and 0xFF) / 255f - 0.5f) / 0.5f  // [-1,1]

        if (isNCHW) {
            // CHW: R-plane → G-plane → B-plane
            var idx = 0
            // R
            for (y in 0 until inH) for (xPix in 0 until inW) {
                val p = pixels[idx++]
                input.putFloat(norm(p ushr 16))
            }
            // G
            idx = 0
            for (y in 0 until inH) for (xPix in 0 until inW) {
                val p = pixels[idx++]
                input.putFloat(norm(p ushr 8))
            }
            // B
            idx = 0
            for (y in 0 until inH) for (xPix in 0 until inW) {
                val p = pixels[idx++]
                input.putFloat(norm(p))
            }
        } else {
            // NHWC: 픽셀마다 R,G,B
            var i = 0
            while (i < pixels.size) {
                val p = pixels[i++]
                input.putFloat(norm(p ushr 16))
                input.putFloat(norm(p ushr 8))
                input.putFloat(norm(p))
            }
        }
        input.rewind()
        Log.d(TAG, "BUF   inputCapacity=${input.capacity()} bytes (expect ${4 * inC * inH * inW})")

        val outBuf = Array(outShape[0]) { FloatArray(outShape[1]) }
        val t0 = System.nanoTime()
        interpreter.run(input, outBuf)
        val ms = (System.nanoTime() - t0) / 1e6
        Log.d(TAG, "RUN   tflite ${"%.2f".format(ms)} ms")

        val scores = outBuf[0]
        var bi = 0
        for (j in 1 until scores.size) if (scores[j] > scores[bi]) bi = j
        val role = roles.getOrElse(bi) { "unknown" }
        Log.d(TAG, "OUT   topIdx=$bi role=$role score=${"%.3f".format(scores[bi])}")

        return role
    }
}