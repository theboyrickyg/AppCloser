package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
 * Rapid Total Clean finished: the happy ram pops up with sparkling blue eyes, smoke pouring
 * off his head and horns from the speed, and "ALL CLEAN" underneath.
 */
class RapidCleanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        // points on the all_clean_ram picture (600 x 481)
        val EYES = arrayOf(floatArrayOf(213f, 200f), floatArrayOf(382f, 202f))
        // where smoke comes off: horn tops, top of the head, outer horns
        val VENTS = arrayOf(
            floatArrayOf(70f, 90f), floatArrayOf(530f, 90f), floatArrayOf(30f, 220f), floatArrayOf(570f, 220f),
            floatArrayOf(160f, 30f), floatArrayOf(440f, 30f), floatArrayOf(300f, 70f), floatArrayOf(60f, 320f), floatArrayOf(540f, 320f)
        )
        const val PUFF_LIFE = 1500f
        const val PUFF_EVERY = 55L
    }

    private val ram: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram,
        BitmapFactory.Options().apply { inScaled = false })
    private val start = SystemClock.uptimeMillis()
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Path()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.05f).toInt())
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
        val oy = w * 0.10f

        // pop in with a little overshoot
        val pop = (now / 380f).coerceAtMost(1f)
        val sc = if (pop < 1f) (sin(pop * PI * 0.75) / sin(PI * 0.75 * 0.75)).toFloat().coerceAtMost(1.08f) else 1f

        canvas.save()
        canvas.scale(sc, sc, w / 2, oy + ram.height * s / 2)

        // smoke behind him, then a thinner layer of it drifting over his horns
        drawSmoke(canvas, now, ox, oy, s, 1f)
        canvas.drawBitmap(ram, null, android.graphics.RectF(ox, oy, ox + ram.width * s, oy + ram.height * s), img)
        drawSmoke(canvas, now, ox, oy, s, 0.6f)

        // sparkling blue eyes
        drawEyes(canvas, now, ox, oy, s)
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

        if (isShown) postInvalidateOnAnimation()
    }

    private fun drawSmoke(canvas: Canvas, now: Long, ox: Float, oy: Float, s: Float, strength: Float) {
        val first = ((now - PUFF_LIFE) / PUFF_EVERY).toLong().coerceAtLeast(0)
        val last = now / PUFF_EVERY
        for (n in first..last) {
            val age = now - n * PUFF_EVERY
            if (age < 0 || age > PUFF_LIFE) continue
            val t = age / PUFF_LIFE
            val vent = VENTS[(rnd(n, 1) * VENTS.size).toInt().coerceAtMost(VENTS.size - 1)]
            val drift = (vent[0] - 300f) / 300f       // outer vents drift outwards
            val x = vent[0] + (drift * 170f + (rnd(n, 2) - 0.5f) * 70f) * t
            val y = vent[1] - (80f + rnd(n, 3) * 70f) * t
            val r = (16f + 70f * t) * (0.7f + rnd(n, 4) * 0.6f)
            val a = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f) * 0.85f * strength
            val cx = ox + x * s
            val cy = oy + y * s
            val rr = r * s
            val grey = (175 + rnd(n, 5) * 50).toInt()
            p.shader = RadialGradient(cx, cy, rr,
                Color.argb((a * 255).toInt(), grey, grey, grey + 8), Color.argb(0, grey, grey, grey),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, rr, p)
        }
        p.shader = null
    }

    private fun drawEyes(canvas: Canvas, now: Long, ox: Float, oy: Float, s: Float) {
        for ((i, e) in EYES.withIndex()) {
            val ph = now / 420f + i * 1.7f
            val tw = (0.55f + 0.45f * sin(ph)).coerceAtLeast(0f)
            val cx = ox + e[0] * s
            val cy = oy + e[1] * s
            val glow = 26f * s * (0.8f + tw * 0.6f)
            p.shader = RadialGradient(cx, cy, glow * 1.6f,
                Color.argb((150 * tw).toInt(), 130, 205, 255), Color.argb(0, 130, 205, 255), Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, glow * 1.6f, p)
            p.shader = null
            drawStar(canvas, cx + 6f * s, cy - 6f * s, (34f + 26f * tw) * s, now / 900f + i, Color.argb((235 * tw).toInt() + 20, 235, 248, 255))
        }
    }

    /** A 4-point twinkle. */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, rot: Float, color: Int) {
        star.reset()
        for (k in 0 until 8) {
            val ang = rot + k * PI.toFloat() / 4
            val rr = if (k % 2 == 0) r else r * 0.16f
            val x = cx + cos(ang) * rr
            val y = cy + sin(ang) * rr
            if (k == 0) star.moveTo(x, y) else star.lineTo(x, y)
        }
        star.close()
        p.color = color
        canvas.drawPath(star, p)
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, r * 0.12f, p)
    }
}
