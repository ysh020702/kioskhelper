package com.example.kioskhelper.vision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.support.image.TensorImage


data class DetectedBox(
    val label: String,
    val score: Float,
    val bbox: android.graphics.RectF   // 입력 비트맵 좌표계(좌상단 0,0)
)

class TfliteTaskObjectDetector(
    ctx: Context,
    modelAsset: String = "model.tflite",
    scoreThreshold: Float = 0.4f,
    maxResults: Int = 5,
    numThreads: Int = 2,
    useGpu: Boolean = false
) {
    private val detector: ObjectDetector

    init {
        val base = BaseOptions.builder()
            .setNumThreads(numThreads)
            .apply { if (useGpu) useGpu() }
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(maxResults)
            .build()

        detector = ObjectDetector.createFromFileAndOptions(ctx, modelAsset, options)
    }

    fun detect(bitmap: Bitmap): List<DetectedBox> {
        // (선택) 전처리 파이프가 필요하면 TensorImageProcessor 사용
        val img = TensorImage.fromBitmap(bitmap)
        val results: List<Detection> = detector.detect(img)
        return results.mapNotNull { d ->
            val cat = d.categories.firstOrNull() ?: return@mapNotNull null
            val r = d.boundingBox // RectF (bitmap 기준)
            DetectedBox(
                label = cat.label,
                score = cat.score,
                bbox = android.graphics.RectF(r)
            )
        }
    }
}