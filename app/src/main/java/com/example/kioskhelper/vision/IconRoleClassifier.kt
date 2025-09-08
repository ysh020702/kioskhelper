package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.kioskhelper.core.Utils
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IconRoleClassifier(ctx: Context, modelAsset: String) {

    private val roles = arrayOf("back","next","confirm","cancel","pay","home","menu","language")
    private val interpreter: Interpreter = Interpreter(Utils.loadModel(ctx, modelAsset))

    init {
        val inTensor = interpreter.getInputTensor(0)
        val outTensor = interpreter.getOutputTensor(0)

        val inShape = inTensor.shape()   // ex) [1, 640, 640, 3]
        val outShape = outTensor.shape() // ex) [1, 8]  or  [1, 5, 8400]

        Log.d("RoleClf", "IN  shape=${inShape.contentToString()}, type=${inTensor.dataType()}")
        Log.d("RoleClf", "OUT shape=${outShape.contentToString()}, type=${outTensor.dataType()}")

        // 선택: 분류기 강제 검증(roles.size=8 가정)
        val expected = roles.size
        require(outShape.size == 2 && outShape[0] == 1 && outShape[1] == expected) {
            "Role classifier expected OUT=[1,$expected], but got ${outShape.contentToString()} — " +
                    "확인: icon16.tflite가 탐지기(예: YOLO)로 잘못 들어오지 않았는지, Utils.loadModel이 올바른 Asset을 여는지"
        }
    }



    fun predictRole(crop: Bitmap): String {
        // 1) 모델 입력 스펙 읽기
        val inTensor = interpreter.getInputTensor(0)

        val outTensor = interpreter.getOutputTensor(0)
        val outShape = outTensor.shape()
        require(outShape.size == 2 && outShape[0] == 1 && outShape[1] == roles.size) {
            "Loaded model is not a role classifier. Expected [1, ${roles.size}], got ${outShape.contentToString()}."
        }



        val inShape = inTensor.shape()        // [1, H, W, 3]
        val inType  = inTensor.dataType()
        val inH = inShape[1]; val inW = inShape[2]

        // 2) 비율 유지 resize(레터박스)로 왜곡 최소화
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

        // 3) 입력 버퍼 작성 (float32/uint8 모두 대응)
        val input: Any = when (inType) {
            org.tensorflow.lite.DataType.FLOAT32 -> {
                val buf = java.nio.ByteBuffer.allocateDirect(4 * inW * inH * 3)
                    .order(java.nio.ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)
                var i = 0
                while (i < px.size) {
                    val p = px[i++]
                    val r = ((p ushr 16) and 0xFF) / 255f
                    val g = ((p ushr  8) and 0xFF) / 255f
                    val b = ( p         and 0xFF) / 255f
                    // 기존 코드와 동일한 정규화: (x-0.5)/0.5 → [-1,1]
                    buf.putFloat((r - 0.5f) / 0.5f)
                    buf.putFloat((g - 0.5f) / 0.5f)
                    buf.putFloat((b - 0.5f) / 0.5f)
                }
                buf.rewind()
                buf
            }
            org.tensorflow.lite.DataType.UINT8 -> {
                val buf = java.nio.ByteBuffer.allocateDirect(inW * inH * 3)
                    .order(java.nio.ByteOrder.nativeOrder())
                val px = IntArray(inW * inH)
                x.getPixels(px, 0, inW, 0, 0, inW, inH)
                var i = 0
                while (i < px.size) {
                    val p = px[i++]
                    buf.put(((p ushr 16) and 0xFF).toByte())
                    buf.put(((p ushr  8) and 0xFF).toByte())
                    buf.put(( p         and 0xFF).toByte())
                }
                buf.rewind()
                buf
            }
            else -> error("Unsupported input dtype: $inType")
        }

        // 4) 출력 버퍼 준비 (출력 차원 안전 처리)
       // val outTensor = interpreter.getOutputTensor(0)
        val outType = outTensor.dataType()
        //val outShape = outTensor.shape() // 보통 [1, numClasses]
        val outBuf: Any = when (outType) {
            org.tensorflow.lite.DataType.FLOAT32 -> Array(outShape[0]) { FloatArray(outShape[1]) }
            org.tensorflow.lite.DataType.UINT8   -> Array(outShape[0]) { ByteArray(outShape[1]) }
            else -> error("Unsupported output dtype: $outType")
        }

        // 5) 추론
        interpreter.run(input, outBuf)

        // 6) argmax
        val scores: FloatArray = when (outType) {
            org.tensorflow.lite.DataType.FLOAT32 -> (outBuf as Array<FloatArray>)[0]
            org.tensorflow.lite.DataType.UINT8   -> {
                val arr = (outBuf as Array<ByteArray>)[0]
                FloatArray(arr.size) { (arr[it].toInt() and 0xFF) / 255f }
            }
            else -> error("Unsupported output dtype: $outType")
        }

        var bi = 0
        for (i in 1 until scores.size) if (scores[i] > scores[bi]) bi = i

        // roles 개수와 출력 차원이 달라도 안전하게
        return if (bi in roles.indices) roles[bi] else roles.getOrElse(bi.coerceAtMost(roles.lastIndex)) { "unknown" }
    }

}