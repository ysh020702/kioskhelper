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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.RED
    }

    // ✅ [추가] 원본 크기와 매핑 행렬
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

    // ✅ [추가] YOLO에 넣었던 원본(Bitmap) 크기 알려주기
    fun setSourceSize(w: Int, h: Int) {
        srcW = w; srcH = h
        computeMatrix()
        invalidate()
    }

    // ✅ [추가] PreviewView.ScaleType.FILL_CENTER(센터-크롭) 매핑
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
        computeMatrix() // 뷰 크기 변하면 재계산
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
            // ✅ [핵심 한 줄] src(px) → view 좌표로 변환
            tmpRect.set(b.rect)
            if (srcW > 0 && srcH > 0) srcToView.mapRect(tmpRect)

            paint.color = if (idsToDraw.contains(b.id)) Color.RED else Color.BLUE
            canvas.drawRoundRect(tmpRect, 18f, 18f, paint)
        }
    }
}