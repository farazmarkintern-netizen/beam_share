package com.example.beamshare.videotoaudio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.beamshare.R

/**
 * Circular progress ring for VA03 converting screen.
 *
 * XML attrs (declare in res/values/attrs_videotoaudio.xml):
 *   cpv_trackColor    → color  (default va_border)
 *   cpv_progressColor → color  (default va_accent)
 *   cpv_strokeWidth   → dimension (default 10dp)
 *   cpv_progress      → float 0–100 (default 0)
 *
 * Usage:
 *   binding.circularProgress.setProgress(70f)
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var trackColor: Int = ContextCompat.getColor(context, R.color.va_border)
    private var progressColor: Int = ContextCompat.getColor(context, R.color.va_accent)
    private var strokeWidthPx: Float = 10f * context.resources.displayMetrics.density
    private var progress: Float = 0f   // 0–100

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.CircularProgressView)
            trackColor    = ta.getColor(R.styleable.CircularProgressView_cpv_trackColor, trackColor)
            progressColor = ta.getColor(R.styleable.CircularProgressView_cpv_progressColor, progressColor)
            strokeWidthPx = ta.getDimension(R.styleable.CircularProgressView_cpv_strokeWidth, strokeWidthPx)
            progress      = ta.getFloat(R.styleable.CircularProgressView_cpv_progress, progress)
            ta.recycle()
        }
        applyPaints()
    }

    private fun applyPaints() {
        trackPaint.color = trackColor
        trackPaint.strokeWidth = strokeWidthPx
        progressPaint.color = progressColor
        progressPaint.strokeWidth = strokeWidthPx
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = strokeWidthPx / 2f
        oval.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        // Track (full circle)
        canvas.drawArc(oval, -90f, 360f, false, trackPaint)
        // Progress arc
        val sweep = 360f * (progress / 100f)
        canvas.drawArc(oval, -90f, sweep, false, progressPaint)
    }

    // ── Public API ─────────────────────────────────────────────────────────
    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 100f)
        invalidate()
    }

    fun getProgress(): Float = progress

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = color
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = color
        invalidate()
    }
}
