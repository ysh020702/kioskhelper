package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import com.example.kioskhelper.core.Utils
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IconRoleClassifier(ctx: Context, modelAsset: String) {
    private val roles = arrayOf("back","next","confirm","cancel","pay","home","menu","language")
    private val interpreter: Interpreter = Interpreter(Utils.loadModel(ctx, modelAsset))


    fun predictRole(crop: Bitmap): String {
        val size = 160
        val x = Bitmap.createScaledBitmap(crop, size, size, true)
        val input = ByteBuffer.allocateDirect(size*size*3*4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(size*size); x.getPixels(pixels,0,size,0,0,size,size)
        for (p in pixels) {
            val r=((p shr 16) and 0xFF)/255f; val g=((p shr 8) and 0xFF)/255f; val b=(p and 0xFF)/255f
            input.putFloat((r-0.5f)/0.5f); input.putFloat((g-0.5f)/0.5f); input.putFloat((b-0.5f)/0.5f)
        }
        val out = Array(1){FloatArray(roles.size)}
        interpreter.run(input, out)
        val scores = out[0]
        var bi = 0
        for (i in 1 until scores.size) if (scores[i] > scores[bi]) bi = i
        return roles[bi]
    }
}