package com.rickyboy.appsweep

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan

/** Draws a word as a rounded button inside a line of text (tapping is handled by a ClickableSpan). */
class PillSpan(private val bg: Int, private val fg: Int, private val density: Float) : ReplacementSpan() {
    private val padH = 4 * density
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        if (fm != null) {
            paint.getFontMetricsInt(fm)
            fm.top -= (4 * density).toInt(); fm.ascent -= (4 * density).toInt()
            fm.bottom += (4 * density).toInt(); fm.descent += (4 * density).toInt()
        }
        return (bold(paint).measureText(text, start, end) + padH * 2).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint
    ) {
        val p = bold(paint)
        val w = p.measureText(text, start, end) + padH * 2
        rect.set(x, y + p.ascent() - 5 * density, x + w, y + p.descent() + 5 * density)
        val oldColor = p.color
        p.color = bg
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, p)
        p.color = fg
        canvas.drawText(text, start, end, x + padH, y.toFloat(), p)
        p.color = oldColor
    }

    private fun bold(paint: Paint): Paint = Paint(paint).apply { isFakeBoldText = true; isAntiAlias = true }
}
