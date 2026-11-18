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
        const val FLAME_LIFE = 650f
        const val FLAME_EVERY = 4L
    }

    private val ram: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram,
        BitmapFactory.Options().apply { inScaled = false })
    private val start = SystemClock.uptimeMillis()
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
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

        // flames licking up behind his horns, then the brighter part over the top of them
        drawFire(canvas, now, ox, oy, s, 0.55f)
        canvas.drawBitmap(ram, null, android.graphics.RectF(ox, oy, ox + ram.width * s, oy + ram.height * s), img)
        drawFire(canvas, now, ox, oy, s, 0.35f)
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

    private val add = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)

    /** Fire along both horns: flickering flame blobs that rise, shrink and cool from yellow to red. */
    private fun drawFire(canvas: Canvas, now: Long, ox: Float, oy: Float, s: Float, strength: Float) {
        p.xfermode = add
        val first = ((now - FLAME_LIFE) / FLAME_EVERY).toLong().coerceAtLeast(0)
        val last = now / FLAME_EVERY
        for (n in first..last) {
            val age = now - n * FLAME_EVERY
            if (age < 0 || age > FLAME_LIFE) continue
            val t = age / FLAME_LIFE
            // a spot along the horn top
            val seg = (rnd(n, 1) * (HORN.size - 1)).toInt().coerceAtMost(HORN.size - 2)
            val f = rnd(n, 2)
            var bx = HORN[seg][0] + (HORN[seg + 1][0] - HORN[seg][0]) * f
            val by = HORN[seg][1] + (HORN[seg + 1][1] - HORN[seg][1]) * f
            if (rnd(n, 3) > 0.5f) bx = 600f - bx           // right horn
            val x = bx + sin(t * 9f + n) * 7f * t
            val y = by - (90f + rnd(n, 4) * 80f) * t + 10f
            val r = (40f * (1f - t) + 10f) * (0.7f + rnd(n, 5) * 0.6f)
            val a = (if (t < 0.12f) t / 0.12f else 1f - (t - 0.12f) / 0.88f) * strength
            // yellow → orange → red as it rises
            val g = (230 - 190 * t).toInt().coerceIn(30, 230)
            val b = (120 - 160 * t).toInt().coerceIn(0, 120)
            val cx = ox + x * s
            val cy = oy + y * s
            val rr = r * s
            p.shader = RadialGradient(cx, cy, rr,
                Color.argb((a * 255).toInt().coerceIn(0, 255), 255, g, b), Color.argb(0, 255, g / 2, 0),
                Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, rr, p)
        }
        p.shader = null
        p.xfermode = null
    }

}
