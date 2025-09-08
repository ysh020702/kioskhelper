package com.example.kioskhelper.core

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.kioskhelper.vision.deprecated.IconRoleClassifier
import com.example.kioskhelper.vision.deprecated.MlKitOcr
import com.example.kioskhelper.vision.deprecated.YoloTfliteDetector
import androidx.camera.core.ImageProxy
import com.example.kioskhelper.presentation.model.ButtonBox
import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView


class RealtimeKioskPipeline(
    private val overlay: DetectionOverlayView,
    private val detector: YoloTfliteDetector,
    private val tracker: SimpleTracker,
    private val ocr: MlKitOcr,
    private val iconCls: IconRoleClassifier,
    private val imageToView: (RectF) -> RectF
) {
    data class ButtonDet(
        val id: Int,
        var rectImg: RectF,
        var rectView: RectF,
        var score: Float,
        var text: String? = null,
        var role: String? = null,
        var ocrTimeMs: Long? = null
    )


    private val rag = RagMatcher()
    private var frameCount = 0
    private var lastButtons = mutableListOf<ButtonDet>()
    private val DETECT_INTERVAL = 3
    private val OCR_TTL_MS = 1500L
    private val MIN_BOX = 28f


    suspend fun onFrame(image: ImageProxy, bitmap: Bitmap) {
        val buttons: MutableList<ButtonDet> = if (frameCount % DETECT_INTERVAL == 0) {
            val dets = detector.detect(bitmap)
                .filter { it.rect.width()>MIN_BOX && it.rect.height()>MIN_BOX }
            val tracked = tracker.update(dets.map { TrkBox(0, it.rect, it.score) })
            tracked.map { tb ->
                ButtonDet(tb.trackId, tb.rect, imageToView(tb.rect), tb.score)
            }.toMutableList()
        } else {
            tracker.predict().map { tb ->
                val old = lastButtons.find { it.id == tb.trackId }
                ButtonDet(tb.trackId, tb.rect, imageToView(tb.rect), tb.score, old?.text, old?.role, old?.ocrTimeMs)
            }.toMutableList()
        }


        val now = System.currentTimeMillis()
        for (b in buttons) {
            val needOcr = b.text.isNullOrBlank() || b.ocrTimeMs==null || now - b.ocrTimeMs!! > OCR_TTL_MS
            if (needOcr) {
                val crop = Utils.cropBitmap(bitmap, b.rectImg, 8)
                val (txt, conf) = ocr.run(crop)
                if (!txt.isNullOrBlank() && conf >= 0.60f) { b.text = txt; b.ocrTimeMs = now }
                else { if (b.role==null) b.role = iconCls.predictRole(crop); b.ocrTimeMs = now }
            }
            b.rectView = imageToView(b.rectImg)
        }
        lastButtons = buttons
        overlay.submitBoxes(buttons.map { ButtonBox(it.id, it.rectView) })
        frameCount++
    }


    fun ragLive(stt: String) = rag.match(stt, lastButtons)
    fun ragFinal(stt: String) = rag.match(stt, lastButtons)


    fun labelOf(b: ButtonDet): String = b.text ?: (b.role ?: "")


    fun forceRedetect() {
        tracker.reset()
        lastButtons.clear()
    }
}