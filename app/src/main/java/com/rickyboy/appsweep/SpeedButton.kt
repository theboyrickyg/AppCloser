package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.Button

/**
 * A button whose first line of text has speed lines: streaks trailing off to the left,
 * and two thin cuts through the letters that fade out across the word.
 */
class SpeedButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Button(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    /** The button's own fill colour, used to "cut" through the letters. */
    var cutColor: Int = 0xFF12121F.toInt()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val l = layout ?: return
        if (l.lineCount == 0) return
        val left = totalPaddingLeft + l.getLineLeft(0)
        val right = totalPaddingLeft + l.getLineRight(0)
        val base = totalPaddingTop + l.getLineBaseline(0).toFloat()
        val cap = -paint.ascent() * 0.72f          // roughly the height of the capital letters
        val top = base - cap
        val w = right - left

        // cuts through the letters, strongest at the front of the word
        p.style = Paint.Style.FILL
        for ((frac, h) in listOf(0.34f to 1.6f * dp, 0.66f to 2.2f * dp)) {
            val y = top + cap * frac
            p.shader = LinearGradient(left, 0f, left + w * 0.75f, 0f,
                cutColor, cutColor and 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRect(left - 2 * dp, y - h / 2, left + w * 0.75f, y + h / 2, p)
        }

        // streaks trailing behind the word
        val streaks = listOf(0.22f to 34f, 0.5f to 22f, 0.78f to 30f)
        for ((frac, len) in streaks) {
            val y = top + cap * frac
            val end = left - 6 * dp
            val start = end - len * dp
            p.shader = LinearGradient(start, 0f, end, 0f,
                currentTextColor and 0x00FFFFFF, currentTextColor, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(start, y - 1.2f * dp, end, y + 1.2f * dp, 1.2f * dp, 1.2f * dp, p)
        }
        p.shader = null
    }
}
