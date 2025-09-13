package com.example.kioskhelper.presentation.overlayview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.example.kioskhelper.presentation.model.ButtonBox
import kotlin.math.sin


class DetectionOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val boxes = mutableListOf<ButtonBox>()

    // 테두리(펄스) 페인트
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.RED
    }

    // ★ 반투명 채우기 페인트 추가
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 90 // 기본 알파 (약간 반투명). 펄스에 따라 onDraw에서 동적으로 조정
    }

    // 원본 크기와 매핑 행렬
    private var srcW: Int = 0
    private var srcH: Int = 0
    private val srcToView = Matrix()
    private val tmpRect = RectF()

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

    fun setSourceSize(w: Int, h: Int) {
        srcW = w; srcH = h
        computeMatrix()
        invalidate()
    }

    private fun computeMatrix() {
        if (srcW <= 0 || srcH <= 0 || width <= 0 || height <= 0) return
        val scale = kotlin.math.max(width.toFloat() / srcW, height.toFloat() / srcH)
        val dx = (width - srcW * scale) / 2f
        val dy = (height - srcH * scale) / 2f
        srcToView.reset()
        srcToView.postScale(scale, scale)
        srcToView.postTranslate(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMatrix()
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 0.5f).apply {
            duration = 800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
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
                addUpdateListener {
                    altIndex = it.animatedValue as Int
                    invalidate()
                }
                start()
            }
        }
    }

    private fun stopAnimators() { pulseAnimator?.cancel(); altAnimator?.cancel() }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimators() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return

        // 테두리용 펄스 알파/두께
        val a = (120 + 80 * sin(pulse * Math.PI)).toInt().coerceIn(0, 255)
        strokePaint.alpha = a
        strokePaint.strokeWidth = 6f + 4f * pulse

        // 채움 알파 (빨간색 전용)
        val fillAlpha = ((a * 0.55f) + 40).toInt().coerceIn(60, 140)

        val idsToDraw: Set<Int> = highlightIds.toSet()

        boxes.forEach { b ->
            tmpRect.set(b.rect)
            if (srcW > 0 && srcH > 0) srcToView.mapRect(tmpRect)

            if (idsToDraw.contains(b.id)) {
                // 🔴 빨간색: 채움 + 테두리
                fillPaint.color = Color.RED
                fillPaint.alpha = fillAlpha
                strokePaint.color = Color.RED

                canvas.drawRoundRect(tmpRect, 18f, 18f, fillPaint)
                canvas.drawRoundRect(tmpRect, 18f, 18f, strokePaint)
            } else {
                // 🔵 파란색: 테두리만 (안은 투명)
                strokePaint.color = Color.BLUE
                strokePaint.alpha = 180  // 펄스 말고 고정값으로 줄 수도 있음
                strokePaint.strokeWidth = 5f

                canvas.drawRoundRect(tmpRect, 18f, 18f, strokePaint)
            }
        }
    }


}