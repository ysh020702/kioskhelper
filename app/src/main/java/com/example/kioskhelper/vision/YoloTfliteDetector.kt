package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.support.image.TensorImage

data class DetOut(val trackHint: Int, val rect: RectF, val score: Float)

class YoloTfliteDetector(
    ctx: Context,
    modelAsset: String,
    private val conf: Float = 0.35f,
    private val maxResults: Int = 100
) {
    private val detector: ObjectDetector

    init {
        val base = BaseOptions.builder()
            .setNumThreads(4)
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setScoreThreshold(conf)
            .setMaxResults(maxResults)
            .build()
        detector = ObjectDetector.createFromFileAndOptions(ctx, modelAsset, options)
    }

    fun detect(bitmap: Bitmap): List<DetOut> {
        val image = TensorImage.fromBitmap(bitmap)
        val results: List<Detection> = detector.detect(image)
        val out = ArrayList<DetOut>(results.size)
        var i = 0
        for (det in results) {
            val bb = det.boundingBox
            val score = det.categories.firstOrNull()?.score ?: 0f
            out += DetOut(i++, RectF(bb.left, bb.top, bb.right, bb.bottom), score)
        }
        return out
    }
}
