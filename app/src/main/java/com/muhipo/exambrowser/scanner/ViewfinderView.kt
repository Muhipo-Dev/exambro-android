package com.muhipo.exambrowser.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.muhipo.exambrowser.R

class ViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scanner_overlay)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_accent_light)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scanner_laser)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    val framingRect = Rect()
    private var laserY = 0f
    private var laserGoingDown = true

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boxSize = (w.coerceAtMost(h) * 0.72f).toInt()
        val left = (w - boxSize) / 2
        val top = (h - boxSize) / 2 - (h * 0.05f).toInt()
        framingRect.set(left, top, left + boxSize, top + boxSize)
        laserY = framingRect.top.toFloat()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (framingRect.width() <= 0) return

        val width = width.toFloat()
        val height = height.toFloat()

        // 1. Draw outer 4 darkened mask rectangles around framing rect
        canvas.drawRect(0f, 0f, width, framingRect.top.toFloat(), maskPaint)
        canvas.drawRect(0f, framingRect.top.toFloat(), framingRect.left.toFloat(), framingRect.bottom.toFloat(), maskPaint)
        canvas.drawRect(framingRect.right.toFloat(), framingRect.top.toFloat(), width, framingRect.bottom.toFloat(), maskPaint)
        canvas.drawRect(0f, framingRect.bottom.toFloat(), width, height, maskPaint)

        // 2. Draw 4 modern corner brackets
        val cornerLength = framingRect.width() * 0.08f
        val left = framingRect.left.toFloat()
        val top = framingRect.top.toFloat()
        val right = framingRect.right.toFloat()
        val bottom = framingRect.bottom.toFloat()

        // Top-Left
        canvas.drawLine(left, top + cornerLength, left, top, cornerPaint)
        canvas.drawLine(left, top, left + cornerLength, top, cornerPaint)

        // Top-Right
        canvas.drawLine(right - cornerLength, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLength, cornerPaint)

        // Bottom-Left
        canvas.drawLine(left, bottom - cornerLength, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLength, bottom, cornerPaint)

        // Bottom-Right
        canvas.drawLine(right - cornerLength, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom, right, bottom - cornerLength, cornerPaint)

        // 3. Draw and animate laser line
        canvas.drawLine(left + 8f, laserY, right - 8f, laserY, laserPaint)

        val speed = 8f
        if (laserGoingDown) {
            laserY += speed
            if (laserY >= bottom - 4f) {
                laserGoingDown = false
            }
        } else {
            laserY -= speed
            if (laserY <= top + 4f) {
                laserGoingDown = true
            }
        }

        // Request next frame animation
        postInvalidateDelayed(16)
    }
}
