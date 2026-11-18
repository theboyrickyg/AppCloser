package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Rapid Total Clean screen. While it cleans, a sports-car speedometer revs up and the
 * ram's horns heat up to a glowing amber. When it's done: "ALL CLEAN" and the time it took.
 */
class RapidCleanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val MAX_SPEED = 320f
        const val START_DEG = 150f      // where 0 sits on the dial
        const val SWEEP_DEG = 240f      // 0 → MAX_SPEED
        const val REDLINE = 260f
    }

    private val opts = BitmapFactory.Options().apply { inScaled = false }
    private val ram: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram, opts)
    private val hot: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram_hot, opts)
    private val halo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.horn_halo, opts)
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val start = SystemClock.uptimeMillis()

    /** 0..100 while cleaning. */
    var progress = 0f
    private var doneAt = 0L
    private var seconds = 0f
    private var shown = 0f              // the needle's speed, eased towards its target

    /** Cleaning finished in [secs] seconds. */
    fun finish(secs: Float) {
        seconds = secs
        doneAt = SystemClock.uptimeMillis()
    }

    val isDone get() = doneAt > 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.56f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val w = width.toFloat()
        val done = doneAt > 0
        val sinceDone = if (done) (now - doneAt).toFloat() else 0f

        // ---- the ram, horns heating up as it goes ----
        val s = w * 0.72f / ram.width
        val ox = (w - ram.width * s) / 2
        val oy = w * 0.07f
        val heat = if (done) 1f else (0.15f + 0.85f * progress / 100f)
        val pulse = 0.82f + 0.18f * sin(now / 260f)
        val glow = (heat * pulse).coerceIn(0f, 1f)

        val pad = (halo.width - ram.width) / 2f
        img.alpha = (glow * 255).toInt()
        r.set(ox - pad * s, oy - pad * s, ox + (ram.width + pad) * s, oy + (ram.height + pad) * s)
        canvas.drawBitmap(halo, null, r, img)
        r.set(ox, oy, ox + ram.width * s, oy + ram.height * s)
        img.alpha = 255
        canvas.drawBitmap(ram, null, r, img)
        img.alpha = (heat * (0.75f + 0.25f * pulse) * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(hot, null, r, img)
        img.alpha = 255

        // ---- ALL CLEAN (pops in when done) ----
        val ty = oy + ram.height * s + w * 0.12f
        t.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        t.letterSpacing = 0.12f
        t.style = Paint.Style.FILL
        if (done) {
            val pop = (sinceDone / 300f).coerceAtMost(1f)
            val sc = if (pop < 1f) 0.6f + 0.5f * pop else 1f
            canvas.save()
            canvas.scale(sc, sc, w / 2, ty)
            t.textSize = w * 0.12f
            t.style = Paint.Style.STROKE
            t.strokeWidth = w * 0.018f
            t.color = Color.rgb(8, 40, 20)
            canvas.drawText("ALL CLEAN", w / 2, ty, t)
            t.style = Paint.Style.FILL
            t.color = Color.rgb(0, 230, 118)
            canvas.drawText("ALL CLEAN", w / 2, ty, t)
            canvas.restore()
        } else {
            t.textSize = w * 0.07f
            t.color = Color.rgb(255, 170, 60)
            canvas.drawText("RAPID CLEANING…", w / 2, ty - w * 0.02f, t)
        }

        // ---- the speedometer ----
        val target = if (done) {
            // eases back off the redline once it's finished
            MAX_SPEED * (0.9f - 0.08f * (sinceDone / 1200f).coerceAtMost(1f))
        } else {
            val base = MAX_SPEED * (0.25f + 0.72f * progress / 100f)
            base + sin(now / 45f) * 6f + sin(now / 17f) * 3f      // engine shake
        }
        shown += (target - shown) * 0.12f
        val elapsed = if (done) seconds else (now - start) / 1000f
        drawSpeedo(canvas, w / 2, ty + w * 0.40f, w * 0.31f, shown, elapsed, done)

        if (isShown) postInvalidateOnAnimation()
    }

    private fun angleFor(speed: Float) = START_DEG + SWEEP_DEG * (speed / MAX_SPEED).coerceIn(0f, 1.02f)

    private fun drawSpeedo(canvas: Canvas, cx: Float, cy: Float, rad: Float, speed: Float, secs: Float, done: Boolean) {
        p.style = Paint.Style.FILL

        // chrome bezel
        p.shader = SweepGradient(cx, cy,
            intArrayOf(0xFF6E7380.toInt(), 0xFFE9ECF2.toInt(), 0xFF5A5F6B.toInt(), 0xFFCDD2DA.toInt(), 0xFF6E7380.toInt()), null)
        canvas.drawCircle(cx, cy, rad * 1.08f, p)
        p.shader = null
        p.color = Color.rgb(12, 12, 16)
        canvas.drawCircle(cx, cy, rad * 1.025f, p)
        // dial face
        p.shader = RadialGradient(cx, cy - rad * 0.3f, rad * 1.2f,
            intArrayOf(0xFF2A2C34.toInt(), 0xFF101117.toInt(), 0xFF050508.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, rad, p)
        p.shader = null

        // red zone
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = rad * 0.07f
        p.color = Color.rgb(220, 30, 30)
        r.set(cx - rad * 0.86f, cy - rad * 0.86f, cx + rad * 0.86f, cy + rad * 0.86f)
        canvas.drawArc(r, angleFor(REDLINE), angleFor(MAX_SPEED) - angleFor(REDLINE), false, p)

        // glowing arc up to the needle
        p.strokeWidth = rad * 0.035f
        p.color = Color.argb(200, 255, 150, 40)
        r.set(cx - rad * 0.93f, cy - rad * 0.93f, cx + rad * 0.93f, cy + rad * 0.93f)
        canvas.drawArc(r, START_DEG, angleFor(speed) - START_DEG, false, p)

        // ticks and numbers
        t.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        t.letterSpacing = 0f
        t.style = Paint.Style.FILL
        t.textSize = rad * 0.13f
        var v = 0
        while (v <= MAX_SPEED.toInt()) {
            val a = Math.toRadians(angleFor(v.toFloat()).toDouble())
            val major = v % 40 == 0
            val r0 = rad * (if (major) 0.70f else 0.76f)
            val r1 = rad * 0.82f
            p.strokeWidth = if (major) rad * 0.025f else rad * 0.012f
            p.color = if (v >= REDLINE) Color.rgb(255, 70, 60) else Color.WHITE
            canvas.drawLine(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0,
                cx + cos(a).toFloat() * r1, cy + sin(a).toFloat() * r1, p)
            if (major) {
                val rt = rad * 0.55f
                t.color = if (v >= REDLINE) Color.rgb(255, 80, 70) else Color.rgb(235, 235, 240)
                canvas.drawText(v.toString(), cx + cos(a).toFloat() * rt, cy + sin(a).toFloat() * rt + t.textSize * 0.36f, t)
            }
            v += 10
        }

        // label
        t.textSize = rad * 0.10f
        t.letterSpacing = 0.2f
        t.color = Color.rgb(150, 150, 165)
        canvas.drawText("CLEAN SPEED", cx, cy - rad * 0.30f, t)

        // digital time readout
        t.letterSpacing = 0.05f
        t.textSize = rad * 0.22f
        t.color = if (done) Color.rgb(0, 230, 118) else Color.rgb(255, 170, 60)
        canvas.drawText(String.format(Locale.UK, "%.1f s", secs), cx, cy + rad * 0.50f, t)
        t.textSize = rad * 0.09f
        t.letterSpacing = 0.2f
        t.color = Color.rgb(150, 150, 165)
        canvas.drawText(if (done) "TIME TO CLEAN" else "CLEANING", cx, cy + rad * 0.66f, t)

        // needle
        val a = Math.toRadians(angleFor(speed).toDouble())
        val nx = cx + cos(a).toFloat() * rad * 0.88f
        val ny = cy + sin(a).toFloat() * rad * 0.88f
        val bx = cx - cos(a).toFloat() * rad * 0.16f
        val by = cy - sin(a).toFloat() * rad * 0.16f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = rad * 0.07f
        p.color = Color.argb(70, 255, 60, 30)
        canvas.drawLine(bx, by, nx, ny, p)          // glow
        p.strokeWidth = rad * 0.03f
        p.shader = LinearGradient(bx, by, nx, ny, Color.rgb(160, 10, 10), Color.rgb(255, 70, 40), Shader.TileMode.CLAMP)
        canvas.drawLine(bx, by, nx, ny, p)
        p.shader = null

        // hub
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - rad * 0.03f, cy - rad * 0.03f, rad * 0.11f,
            intArrayOf(0xFFE0E3EA.toInt(), 0xFF50545E.toInt(), 0xFF1A1B20.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, rad * 0.10f, p)
        p.shader = null
    }
}
