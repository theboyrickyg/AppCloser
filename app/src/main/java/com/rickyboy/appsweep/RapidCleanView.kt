package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rapid Total Clean finished: the happy blue-eyed ram pops up with his horns on fire
 * from the speed, and "ALL CLEAN" underneath.
 */
class RapidCleanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        // points on the all_clean_ram picture (600 x 481)
        // along the tops of his horns (left horn; the right one is the mirror image)
        val HORN = arrayOf(
            floatArrayOf(28f, 230f), floatArrayOf(30f, 160f), floatArrayOf(55f, 95f), floatArrayOf(95f, 45f),
            floatArrayOf(150f, 18f), floatArrayOf(210f, 22f), floatArrayOf(250f, 55f)
        )
                const val FLAME_EVERY = 4L
    }

    private val ram: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram,
        BitmapFactory.Options().apply { inScaled = false })
    private val start = SystemClock.uptimeMillis()
    /** How long the clean took, shown under ALL CLEAN (0 = not known). */
    var seconds: Float = 0f
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.38f).toInt())
    }

    /** A repeatable "random" number 0..1 for puff n. */
    private fun rnd(n: Long, salt: Int): Float {
        var x = n * 374761393L + salt * 668265263L
        x = (x xor (x ushr 13)) * 1274126177L
        return ((x xor (x ushr 16)) and 0xFFFF).toFloat() / 0xFFFF
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis() - start
        val w = width.toFloat()
        val s = w * 0.86f / ram.width                 // picture scale
        val ox = (w - ram.width * s) / 2
        val oy = w * 0.20f   // room above for the flames

        // pop in with a little overshoot
        val pop = (now / 380f).coerceAtMost(1f)
        val sc = if (pop < 1f) (sin(pop * PI * 0.75) / sin(PI * 0.75 * 0.75)).toFloat().coerceAtMost(1.08f) else 1f

        canvas.save()
        canvas.scale(sc, sc, w / 2, oy + ram.height * s / 2)

        // flames licking up behind his horns, then the brighter part over the top of them
        drawFire(canvas, now, ox, oy, s, 1.0f)
        canvas.drawBitmap(ram, null, android.graphics.RectF(ox, oy, ox + ram.width * s, oy + ram.height * s), img)
        drawFire(canvas, now, ox, oy, s, 0.5f)
        canvas.restore()

        // ALL CLEAN
        val ty = oy + ram.height * s + w * 0.17f
        text.textSize = w * 0.12f
        text.style = Paint.Style.STROKE
        text.strokeWidth = w * 0.018f
        text.color = Color.rgb(8, 40, 20)
        canvas.drawText("ALL CLEAN", w / 2, ty, text)
        text.style = Paint.Style.FILL
        text.color = Color.rgb(0, 230, 118)
        canvas.drawText("ALL CLEAN", w / 2, ty, text)

        // how rapid it was, and that the ram is on fire
        sub.typeface = Typeface.DEFAULT
        sub.textSize = w * 0.05f
        sub.color = Color.WHITE
        val line1 = if (seconds > 0f) "Everything closed in " + String.format(java.util.Locale.UK, "%.1f", seconds) + " seconds"
            else "Everything closed in seconds"
        canvas.drawText(line1, w / 2, ty + w * 0.10f, sub)
        sub.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        sub.textSize = w * 0.06f
        sub.color = Color.rgb(255, 138, 30)
        canvas.drawText("So rapid, this ram is on fire! 🔥", w / 2, ty + w * 0.19f, sub)

        if (isShown) postInvalidateOnAnimation()
    }

    private val add = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)

    /** A flame tongue, 1 tall: pointed tip at the top, round belly at the bottom. */
    private val tongue = android.graphics.Path().apply {
        moveTo(0f, -1f)
        cubicTo(0.30f, -0.55f, 0.48f, -0.05f, 0.30f, 0.22f)
        cubicTo(0.18f, 0.40f, -0.18f, 0.40f, -0.30f, 0.22f)
        cubicTo(-0.48f, -0.05f, -0.30f, -0.55f, 0f, -1f)
        close()
    }

    // three kinds of flame: big dim outer tongues, orange middles, small white-hot cores.
    // Each has a few colour stages as it cools on the way up.
    private val kinds = arrayOf(
        // height, width scale, life ms, rise, base alpha
        floatArrayOf(115f, 0.90f, 720f, 95f, 0.75f),
        floatArrayOf(82f, 0.80f, 560f, 75f, 0.90f),
        floatArrayOf(44f, 0.75f, 380f, 40f, 1.00f)
    )
    private val kindColors = arrayOf(
        intArrayOf(0xFFFF5A12.toInt(), 0xFFE23A0C.toInt(), 0xFFB8200A.toInt(), 0xFF7A1206.toInt()),
        intArrayOf(0xFFFFA224.toInt(), 0xFFFF7A16.toInt(), 0xFFF0480E.toInt(), 0xFFB82A0A.toInt()),
        intArrayOf(0xFFFFF4C8.toInt(), 0xFFFFE07A.toInt(), 0xFFFFB43A.toInt(), 0xFFFF8A1E.toInt())
    )
    private val shaders = Array(3) { k ->
        Array(4) { c ->
            val col = kindColors[k][c]
            RadialGradient(0f, 0.12f, 0.95f,
                intArrayOf(col, (col and 0x00FFFFFF) or (0xAA shl 24), col and 0x00FFFFFF),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }
    }

    /** Fire along both horns. */
    private fun drawFire(canvas: Canvas, now: Long, ox: Float, oy: Float, s: Float, strength: Float) {
        p.xfermode = add
        val maxLife = 720f
        val first = ((now - maxLife) / FLAME_EVERY).toLong().coerceAtLeast(0)
        val last = now / FLAME_EVERY
        for (n in first..last) {
            val kind = when {
                rnd(n, 9) < 0.34f -> 0
                rnd(n, 9) < 0.72f -> 1
                else -> 2
            }
            val spec = kinds[kind]
            val age = now - n * FLAME_EVERY
            if (age < 0 || age > spec[2]) continue
            val t = age / spec[2]
            // a spot along the top of a horn
            val seg = (rnd(n, 1) * (HORN.size - 1)).toInt().coerceAtMost(HORN.size - 2)
            val f = rnd(n, 2)
            var bx = HORN[seg][0] + (HORN[seg + 1][0] - HORN[seg][0]) * f
            val by = HORN[seg][1] + (HORN[seg + 1][1] - HORN[seg][1]) * f
            val right = rnd(n, 3) > 0.5f
            if (right) bx = 600f - bx
            // rise, flicker and sway like real flame
            val sway = sin(now / 95f + n * 1.3f) * 6f * t + sin(now / 47f + n) * 2.5f
            val x = bx + sway
            val y = by + 8f - spec[3] * t * (0.8f + rnd(n, 4) * 0.5f)
            val flick = 0.85f + 0.25f * sin(now / 38f + n * 2.1f)
            val h = spec[0] * (1f - 0.55f * t) * flick * (0.75f + rnd(n, 5) * 0.5f)
            val wScale = spec[1] * (1f - 0.25f * t)
            val a = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f) * spec[4] * strength
            val stage = (t * 4).toInt().coerceIn(0, 3)

            canvas.save()
            canvas.translate(ox + x * s, oy + y * s)
            canvas.rotate(sway * 1.6f + (if (right) 4f else -4f))
            canvas.scale(h * s * wScale, h * s)
            p.shader = shaders[kind][stage]
            p.alpha = (a * 255).toInt().coerceIn(0, 255)
            canvas.drawPath(tongue, p)
            canvas.restore()
        }
        p.shader = null
        p.alpha = 255
        p.xfermode = null
    }
}
