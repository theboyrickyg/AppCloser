package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * The sci-fi "HUD" look: panels and buttons with cut-off corners, a glowing neon edge,
 * little accent bars on the corners, and (for the advanced button) hazard stripes.
 */
class HudDrawable(
    private val density: Float,
    private val edge: Int,                 // neon edge colour
    private val fillTop: Int,
    private val fillBottom: Int,
    private val cut: Float = 14f,          // corner cut, dp
    private val glow: Boolean = true,
    private val hazard: Boolean = false,
    private val accents: Boolean = true
) : Drawable() {

    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var pressed = false
    private var focused = false

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val p = state.contains(android.R.attr.state_pressed)
        val f = state.contains(android.R.attr.state_focused)
        val changed = p != pressed || f != focused
        pressed = p
        focused = f
        return changed
    }

    private fun shape(l: Float, t: Float, r: Float, b: Float, c: Float) {
        path.reset()
        path.moveTo(l + c, t)
        path.lineTo(r - c * 0.5f, t)
        path.lineTo(r, t + c * 0.5f)
        path.lineTo(r, b - c)
        path.lineTo(r - c, b)
        path.lineTo(l + c * 0.5f, b)
        path.lineTo(l, b - c * 0.5f)
        path.lineTo(l, t + c)
        path.close()
    }

    override fun draw(canvas: Canvas) {
        val bnd = bounds
        val d = density
        val inset = if (glow) 5 * d else 1.5f * d
        val l = bnd.left + inset
        val t = bnd.top + inset
        val r = bnd.right - inset
        val b = bnd.bottom - inset
        val c = cut * d
        shape(l, t, r, b, c)

        // fill
        val boost = if (pressed) 1.35f else if (focused) 1.2f else 1f
        fill.shader = LinearGradient(0f, t, 0f, b, brighten(fillTop, boost), brighten(fillBottom, boost), Shader.TileMode.CLAMP)
        canvas.drawPath(path, fill)
        fill.shader = null

        // hazard stripes (advanced button)
        if (hazard) {
            canvas.save()
            canvas.clipPath(path)
            fill.color = Color.argb(70, Color.red(edge), Color.green(edge), Color.blue(edge))
            val step = 14 * d
            var x = l - (b - t)
            while (x < r) {
                val stripe = Path().apply {
                    moveTo(x, b); lineTo(x + step * 0.5f, b); lineTo(x + step * 0.5f + (b - t), t); lineTo(x + (b - t), t); close()
                }
                canvas.drawPath(stripe, fill)
                x += step
            }
            canvas.restore()
            // keep the middle readable
            fill.shader = LinearGradient(l, 0f, r, 0f,
                intArrayOf(0x00000000, 0xE60A1622.toInt(), 0xE60A1622.toInt(), 0x00000000),
                floatArrayOf(0f, 0.22f, 0.78f, 1f), Shader.TileMode.CLAMP)
            canvas.save(); canvas.clipPath(path); canvas.drawRect(l, t, r, b, fill); canvas.restore()
            fill.shader = null
        }

        // soft neon glow
        if (glow) {
            for (i in 3 downTo 1) {
                line.strokeWidth = i * 3.2f * d
                line.color = Color.argb(if (focused || pressed) 55 else 30, Color.red(edge), Color.green(edge), Color.blue(edge))
                canvas.drawPath(path, line)
            }
        }
        // crisp edge
        line.strokeWidth = (if (focused) 2.6f else 1.6f) * d
        line.color = edge
        canvas.drawPath(path, line)

        // accent bars on two corners
        if (accents) {
            line.strokeWidth = 3.2f * d
            line.strokeCap = Paint.Cap.SQUARE
            val len = minOf(40 * d, (r - l) * 0.18f)
            canvas.drawLine(l + c + 4 * d, t + 3 * d, l + c + 4 * d + len, t + 3 * d, line)
            canvas.drawLine(r - c - 4 * d - len, b - 3 * d, r - c - 4 * d, b - 3 * d, line)
            line.strokeCap = Paint.Cap.BUTT
        }
    }

    private fun brighten(c: Int, k: Float): Int = if (k == 1f) c else Color.argb(
        Color.alpha(c),
        (Color.red(c) * k).toInt().coerceAtMost(255),
        (Color.green(c) * k).toInt().coerceAtMost(255),
        (Color.blue(c) * k).toInt().coerceAtMost(255)
    )

    // no padding of its own: views keep the padding set in the layout
    override fun getPadding(padding: Rect): Boolean = false

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The app's HUD colours and ready-made backgrounds. */
object Hud {
    const val CYAN = 0xFF35D7FF.toInt()
    const val BLUE = 0xFF2E8BFF.toInt()
    const val GREEN = 0xFF2EE68A.toInt()
    const val PURPLE = 0xFFB57BFF.toInt()
    const val AMBER = 0xFFFFC640.toInt()
    const val RED = 0xFFFF4D5E.toInt()

    private fun d(ctx: Context) = ctx.resources.displayMetrics.density

    /** Big panel (rams, setup steps, app list). */
    fun panel(ctx: Context, edge: Int = CYAN) =
        HudDrawable(d(ctx), edge, 0xE60B1A28.toInt(), 0xE6081320.toInt(), cut = 18f)

    /** Main action buttons. */
    fun button(ctx: Context, edge: Int) = HudDrawable(
        d(ctx), edge,
        Color.argb(255, Color.red(edge) / 5, Color.green(edge) / 5 + 8, Color.blue(edge) / 4 + 14),
        Color.argb(255, Color.red(edge) / 9, Color.green(edge) / 9 + 6, Color.blue(edge) / 7 + 10),
        cut = 16f
    )

    /** The three result boxes. */
    fun statBox(ctx: Context, edge: Int) = HudDrawable(
        d(ctx), edge,
        Color.argb(255, Color.red(edge) / 7, Color.green(edge) / 7 + 6, Color.blue(edge) / 6 + 10),
        0xFF08121E.toInt(), cut = 12f, glow = true, accents = false
    )

    /** Small rows (list items, the Recents switch). */
    fun row(ctx: Context, edge: Int = 0xFF1E4A66.toInt()) =
        HudDrawable(d(ctx), edge, 0xF00C1A27.toInt(), 0xF0091521.toInt(), cut = 10f, glow = false, accents = false)

    /** The yellow-and-black striped "advanced" button. */
    fun hazard(ctx: Context) =
        HudDrawable(d(ctx), AMBER, 0xFF1A1606.toInt(), 0xFF0F0D05.toInt(), cut = 12f, hazard = true, accents = false)
}
