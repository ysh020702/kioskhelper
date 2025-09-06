package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import com.example.kioskhelper.core.Utils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class MlKitOcr(ctx: Context) {
    private val client = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    suspend fun run(crop: Bitmap): Pair<String?, Float> = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(crop, 0))
            .addOnSuccessListener { txt ->
                var best = "" to 0f
                txt.textBlocks.forEach { block ->
                    val s = block.text.trim()
                    if (s.isNotEmpty() && s.length > best.first.length) best = s to 0.99f
                }
                cont.resume(best)
            }.addOnFailureListener { cont.resume("" to 0f) }
    }
}


