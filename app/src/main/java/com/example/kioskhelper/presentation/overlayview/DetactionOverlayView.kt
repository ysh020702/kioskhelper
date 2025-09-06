package com.example.kioskhelper.presentation.overlayview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlin.math.sin


class DetectionOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {


    private val boxes = mutableListOf<ButtonBox>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.RED
    }


    private var pulse = 0f
    private var highlightIds: List<Int> = emptyList()
    private var amb = false
    private var pulseAnimator: ValueAnimator? = null
    private var altAnimator: ValueAnimator? = null
    private var altIndex = 0


    fun submitBoxes(newBoxes: List<ButtonBox>) {
        boxes.clear(); boxes.addAll(newBoxes)
        invalidate()
    }


    fun highlight(ids: List<Int>, ambiguous: Boolean) {
        highlightIds = ids
        amb = ambiguous
        startPulse(); startAlternationIfNeeded()
    }


    fun clearHighlight() {
        highlightIds = emptyList(); amb = false
        stopAnimators(); invalidate()
    }


    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }


    private fun startAlternationIfNeeded() {
        altAnimator?.cancel()
        if (amb && highlightIds.size >= 2) {
            altIndex = 0
            altAnimator = ValueAnimator.ofInt(0, 1).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { altIndex = it.animatedValue as Int; invalidate() }
                start()
            }
        }
    }


    private fun stopAnimators() { pulseAnimator?.cancel(); altAnimator?.cancel() }


    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimators() }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return
        val a = (120 + 80 * sin(pulse * Math.PI)).toInt().coerceIn(0, 255)
        paint.alpha = a
        paint.strokeWidth = 6f + 4f * pulse


        val idsToDraw = when {
            amb && highlightIds.size >= 2 -> setOf(highlightIds[altIndex])
            else -> highlightIds.toSet()
        }
        boxes.forEach { b ->
            paint.color = if (idsToDraw.contains(b.id)) Color.RED else Color.argb(80, 200, 200, 200)
            canvas.drawRoundRect(b.rect, 18f, 18f, paint)
        }
    }
}