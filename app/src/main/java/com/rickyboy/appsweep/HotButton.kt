package com.rickyboy.appsweep

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * The hot Rapid button: a glowing orange HUD panel with flames licking off the corners,
 * a speedometer at each end and tyre-track swooshes round them. Text sits between the dials,
 * so give the button about [SIDE_DP] of padding left and right.
 */
class HotButtonDrawable(private val density: Float) : Drawable() {

    companion object {
        const val SIDE_DP = 62
        private const val ORANGE = 0xFFFF8A1E.toInt()
        private const val AMBER = 0xFFFFB23A.toInt()
    }

    private val path = Path()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    private val tongue = Path()
    private var pressed = false

    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        val pr = state.contains(android.R.attr.state_pressed) || state.contains(android.R.attr.state_focused)
        val changed = pr != pressed
        pressed = pr
        if (changed) invalidateSelf()
        return changed
    }

    private fun frame(l: Float, t: Float, r: Float, b: Float, c: Float) {
        path.reset()
        path.moveTo(l + c, t)
        path.lineTo(r - c, t)
        path.lineTo(r, t + c)
        path.lineTo(r, b - c)
        path.lineTo(r - c, b)
        path.lineTo(l + c, b)
        path.lineTo(l, b - c)
        path.lineTo(l, t + c)
        path.close()
    }

    override fun draw(canvas: Canvas) {
        val d = density
        val bnd = bounds
        val l = bnd.left + 9 * d
        val t = bnd.top + 9 * d
        val r = bnd.right - 9 * d
        val b = bnd.bottom - 9 * d
        val w = r - l
        val h = b - t
        val c = 12 * d
        val heat = if (pressed) 1.25f else 1f

        // flames behind the frame, licking off the four corners
        for ((x, y, ang) in listOf(
            Triple(l + 4 * d, t + 6 * d, -125f), Triple(l + c, t, -100f),
            Triple(r - 4 * d, t + 6 * d, -55f), Triple(r - c, t, -80f),
            Triple(l + 2 * d, b - 8 * d, 160f), Triple(r - 2 * d, b - 8 * d, 20f),
            Triple(l + w * 0.18f, t, -95f), Triple(r - w * 0.18f, t, -85f)
        )) drawFlame(canvas, x, y, ang, minOf(h * 0.42f, 26 * d) * heat)

        frame(l, t, r, b, c)
        // outer glow: a few soft strokes
        for (i in 4 downTo 1) {
            line.strokeWidth = i * 3.2f * d
            line.color = withAlpha(ORANGE, (if (pressed) 46 else 32) - i * 5)
            canvas.drawPath(path, line)
        }
        // fill: hot in the middle, darker at the ends
        p.shader = RadialGradient(l + w / 2, t + h / 2, w * 0.55f,
            intArrayOf(if (pressed) 0xFFA84410.toInt() else 0xFF8A340A.toInt(), 0xFF4A1A06.toInt(), 0xFF240C04.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
        p.shader = LinearGradient(0f, t, 0f, b, 0x30FFD08A, 0x00000000, Shader.TileMode.CLAMP)
        canvas.drawPath(path, p)
        p.shader = null

        // double neon edge
        line.strokeWidth = 2.2f * d
        line.shader = LinearGradient(l, 0f, r, 0f,
            intArrayOf(0xFFFF5A12.toInt(), 0xFFFFC870.toInt(), 0xFFFF5A12.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, line)
        line.shader = null
        frame(l + 4 * d, t + 4 * d, r - 4 * d, b - 4 * d, c * 0.8f)
        line.strokeWidth = 1f * d
        line.color = withAlpha(AMBER, 120)
        canvas.drawPath(path, line)

        // a speedometer at each end, with tyre tracks swooshing round it
        val rad = minOf(h * 0.30f, 21 * d)
        val gy = t + h / 2
        drawGauge(canvas, l + 8 * d + rad, gy, rad, -1f)
        drawGauge(canvas, r - 8 * d - rad, gy, rad, 1f)
    }

    private fun drawGauge(canvas: Canvas, cx: Float, cy: Float, rad: Float, side: Float) {
        val d = density
        // burning tyre tracks: two treads sweeping from under the dial up towards the middle
        val inward = -side                                // towards the middle of the button
        for (k in 0..1) {
            val off = k * 8.5f * d
            path.reset()
            path.moveTo(cx - inward * rad * 0.5f, cy + rad + 7 * d - off * 0.4f)
            path.cubicTo(cx + inward * rad * 0.9f, cy + rad + 6 * d - off,
                cx + inward * rad * 1.7f, cy + rad * 0.1f - off,
                cx + inward * rad * 2.0f, cy - rad * 1.05f - off * 0.3f)
            drawTread(canvas, inward)
        }
        // dial
        p.color = 0xE0140703.toInt()
        canvas.drawCircle(cx, cy, rad, p)
        line.strokeWidth = 1.8f * d
        line.color = ORANGE
        canvas.drawCircle(cx, cy, rad, line)
        // ticks round the top three quarters, the last few red
        val oval = RectF(cx - rad * 0.8f, cy - rad * 0.8f, cx + rad * 0.8f, cy + rad * 0.8f)
        line.strokeWidth = 1.2f * d
        for (i in 0..12) {
            val a = Math.toRadians((135 + i * 22.5).toDouble())
            val ca = Math.cos(a).toFloat()
            val sa = Math.sin(a).toFloat()
            line.color = if (i >= 10) 0xFFFF3A1A.toInt() else AMBER
            canvas.drawLine(cx + ca * rad * 0.66f, cy + sa * rad * 0.66f, cx + ca * rad * 0.84f, cy + sa * rad * 0.84f, line)
        }
        line.strokeWidth = 2f * d
        line.color = withAlpha(ORANGE, 160)
        canvas.drawArc(oval, 135f, 270f, false, line)
        // digital readout
        txt.color = AMBER
        txt.textSize = rad * 0.62f
        txt.setShadowLayer(4 * d, 0f, 0f, ORANGE)
        canvas.drawText("88", cx, cy + rad * 0.22f, txt)
        txt.textSize = rad * 0.24f
        txt.clearShadowLayer()
        canvas.drawText("km/h", cx, cy + rad * 0.62f, txt)
    }

    private val measure = android.graphics.PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    /** A glowing tyre tread along [path]: hot glow, orange band, bright chevron blocks, fading at the far end. */
    private fun drawTread(canvas: Canvas, side: Float) {
        val d = density
        val tw = 6.5f * d
        line.strokeCap = Paint.Cap.ROUND
        line.strokeWidth = tw * 2.4f
        line.color = withAlpha(0xFFFF6A10.toInt(), 50)
        canvas.drawPath(path, line)                       // glow
        line.strokeWidth = tw
        line.color = 0xFFC2410C.toInt()
        canvas.drawPath(path, line)                       // the band
        line.strokeCap = Paint.Cap.BUTT
        measure.setPath(path, false)
        val len = measure.length
        var at = 2 * d
        line.strokeWidth = 1.7f * d
        while (at < len) {
            measure.getPosTan(at, pos, tan)
            val f = 1f - at / len                         // brightest near the dial, fading out
            val nx = -tan[1]
            val ny = tan[0]
            val hw = tw * 0.42f
            val lean = 2.2f * d * side
            line.color = withAlpha(if (f > 0.5f) 0xFFFFE08A.toInt() else 0xFFFFA53A.toInt(), (70 + 185 * f).toInt())
            // chevron: two strokes meeting in the middle of the band
            canvas.drawLine(pos[0] + nx * hw, pos[1] + ny * hw, pos[0] + tan[0] * lean, pos[1] + tan[1] * lean, line)
            canvas.drawLine(pos[0] - nx * hw, pos[1] - ny * hw, pos[0] + tan[0] * lean, pos[1] + tan[1] * lean, line)
            at += 4.2f * d
        }
        // flames trailing off the end of the track
        measure.getPosTan(len, pos, tan)
        val ang = Math.toDegrees(Math.atan2(tan[1].toDouble(), tan[0].toDouble())).toFloat()
        drawFlame(canvas, pos[0], pos[1], ang, 16 * d)
    }

    /** One flame with its base at (x, y), pointing at [angle] degrees (0 = right, -90 = up). */
    private fun drawFlame(canvas: Canvas, x: Float, y: Float, angle: Float, len: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        val hw = len * 0.28f
        flameShape(len, hw)
        p.shader = LinearGradient(0f, 0f, len, 0f,
            intArrayOf(0xEEFF7A18.toInt(), 0xCCE8340C.toInt(), 0x00B81E06), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(tongue, p)
        flameShape(len * 0.6f, hw * 0.5f)
        p.shader = LinearGradient(0f, 0f, len * 0.6f, 0f,
            intArrayOf(0xFFFFF0B0.toInt(), 0xDDFFC23A.toInt(), 0x00FF8A1E), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(tongue, p)
        p.shader = null
        canvas.restore()
    }

    private fun flameShape(len: Float, hw: Float) {
        tongue.reset()
        tongue.moveTo(0f, -hw)
        tongue.cubicTo(len * 0.35f, -hw * 1.2f, len * 0.6f, -hw * 0.1f, len, -hw * 0.4f)
        tongue.cubicTo(len * 0.7f, hw * 0.3f, len * 0.4f, hw * 1.1f, 0f, hw)
        tongue.cubicTo(-hw * 0.5f, hw, -hw * 0.5f, -hw, 0f, -hw)
        tongue.close()
    }

    private fun withAlpha(color: Int, a: Int) = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
