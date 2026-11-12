package com.example.datawatch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var pct = 0
    private var hasLimit = false
    private var label = "--"

    init {
        val d = resources.displayMetrics.density
        track.style = Paint.Style.STROKE
        track.strokeWidth = 11f * d
        track.color = 0xFF1C2940.toInt()
        arc.style = Paint.Style.STROKE
        arc.strokeWidth = 11f * d
        arc.strokeCap = Paint.Cap.ROUND
        arc.color = 0xFF4FC3F7.toInt()
        txt.textAlign = Paint.Align.CENTER
        txt.color = 0xFFE8EEF7.toInt()
        txt.isFakeBoldText = true
        txt.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 24f, resources.displayMetrics
        )
    }

    fun setRing(color: Int, percent: Int, limit: Boolean, text: String) {
        arc.color = color
        pct = percent.coerceIn(0, 100)
        hasLimit = limit
        label = text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = arc.strokeWidth
        val size = minOf(width, height).toFloat()
        val left = (width - size) / 2f + s / 2f
        val top = (height - size) / 2f + s / 2f
        box.set(left, top, left + size - s, top + size - s)
        canvas.drawOval(box, track)
        if (hasLimit && pct > 0) {
            canvas.drawArc(box, -90f, 360f * pct / 100f, false, arc)
        }
        val y = height / 2f - (txt.descent() + txt.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, txt)
    }
}
