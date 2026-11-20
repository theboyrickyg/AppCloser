package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.Button

/**
 * A button whose first line of text has fire shooting out sideways from both ends:
 * flames trailing off before the first word and after the last, so it looks fast.
 */
class SpeedButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : Button(context, attrs) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dp = resources.displayMetrics.density
    private val tongue = Path()
    /** Off for illustrated themes, whose button pictures have their own decorations. */
    var fire = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!fire) return
        val l = layout ?: return
        if (l.lineCount == 0) return
        val left = totalPaddingLeft + l.getLineLeft(0)
        val right = totalPaddingLeft + l.getLineRight(0)
        val base = totalPaddingTop + l.getLineBaseline(0).toFloat()
        val cap = -paint.ascent() * 0.72f          // roughly the height of the capital letters
        val mid = base - cap / 2

        drawFire(canvas, left - 9 * dp, mid, cap, -1f)    // before RAPID, pointing left
        drawFire(canvas, right + 9 * dp, mid, cap, 1f)    // after CLEAN, pointing right
    }

    /** Sideways flames with their round base at (x, y), streaming out in direction [dir]. */
    private fun drawFire(canvas: Canvas, x: Float, y: Float, cap: Float, dir: Float) {
        val len = 56 * dp
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir, 1f)
        // three tongues: a long middle one and two shorter ones above and below
        val tongues = arrayOf(
            floatArrayOf(-cap * 0.40f, 0.72f, 0.26f),   // y offset, length share, thickness share
            floatArrayOf(cap * 0.40f, 0.62f, 0.24f),
            floatArrayOf(0f, 1.00f, 0.40f)
        )
        for (tg in tongues) {
            val ty = tg[0]
            val tl = len * tg[1]
            val hw = cap * tg[2]
            // outer red-orange flame
            flameShape(ty, tl, hw)
            p.shader = LinearGradient(0f, 0f, tl, 0f,
                intArrayOf(0xFFFF7A18.toInt(), 0xFFE8340C.toInt(), 0x00B81E06), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(tongue, p)
            // yellow-white hot core
            flameShape(ty, tl * 0.62f, hw * 0.55f)
            p.shader = LinearGradient(0f, 0f, tl * 0.62f, 0f,
                intArrayOf(0xFFFFF6C8.toInt(), 0xFFFFC23A.toInt(), 0x00FF8A1E), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawPath(tongue, p)
        }
        p.shader = null
        canvas.restore()
    }

    /** A flame tongue lying on its side: round base at x=0, wavy pointed tip at x=len. */
    private fun flameShape(y: Float, len: Float, hw: Float) {
        tongue.reset()
        tongue.moveTo(0f, y - hw)
        tongue.cubicTo(len * 0.35f, y - hw * 1.15f, len * 0.62f, y - hw * 0.15f, len, y - hw * 0.35f)   // top edge out to the tip
        tongue.cubicTo(len * 0.70f, y + hw * 0.25f, len * 0.40f, y + hw * 1.10f, 0f, y + hw)            // back along the bottom
        tongue.cubicTo(-hw * 0.4f, y + hw, -hw * 0.4f, y - hw, 0f, y - hw)                               // round base
        tongue.close()
    }
}
