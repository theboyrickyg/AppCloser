package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.sin

/**
 * Rapid Total Clean finished: the smiling ram with his horns glowing amber,
 * "ALL CLEAN" and the time the clean took.
 */
class RapidCleanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val opts = BitmapFactory.Options().apply { inScaled = false }
    private val ram: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram, opts)
    private val hot: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.all_clean_ram_hot, opts)
    private val halo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.horn_halo, opts)
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val start = SystemClock.uptimeMillis()

    /** How long the clean took, in seconds. */
    var seconds = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * 1.08f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val since = (now - start).toFloat()
        val w = width.toFloat()

        // pop in
        val pop = (since / 320f).coerceAtMost(1f)
        val sc = if (pop < 1f) 0.7f + 0.38f * pop else 1f
        canvas.save()
        canvas.scale(sc, sc, w / 2, w * 0.45f)

        // the smiling ram, horns glowing amber like embers (gently pulsing)
        val s = w * 0.80f / ram.width
        val ox = (w - ram.width * s) / 2
        val oy = w * 0.07f
        val heat = (since / 700f).coerceAtMost(1f)            // horns warm up as he appears
        val pulse = 0.82f + 0.18f * sin(now / 260f)
        val pad = (halo.width - ram.width) / 2f
        img.alpha = (heat * pulse * 255).toInt().coerceIn(0, 255)
        r.set(ox - pad * s, oy - pad * s, ox + (ram.width + pad) * s, oy + (ram.height + pad) * s)
        canvas.drawBitmap(halo, null, r, img)
        r.set(ox, oy, ox + ram.width * s, oy + ram.height * s)
        img.alpha = 255
        canvas.drawBitmap(ram, null, r, img)
        img.alpha = (heat * (0.75f + 0.25f * pulse) * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(hot, null, r, img)
        img.alpha = 255
        canvas.restore()

        // ALL CLEAN
        val ty = oy + ram.height * s + w * 0.15f
        t.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        t.letterSpacing = 0.12f
        t.textSize = w * 0.12f
        t.style = Paint.Style.STROKE
        t.strokeWidth = w * 0.018f
        t.color = Color.rgb(8, 40, 20)
        canvas.drawText("ALL CLEAN", w / 2, ty, t)
        t.style = Paint.Style.FILL
        t.color = Color.rgb(0, 230, 118)
        canvas.drawText("ALL CLEAN", w / 2, ty, t)

        // the time it took
        t.letterSpacing = 0.02f
        t.typeface = Typeface.DEFAULT
        t.textSize = w * 0.055f
        t.color = Color.WHITE
        val time = if (seconds > 0f) String.format(Locale.UK, "%.1f", seconds) + " seconds" else "seconds"
        canvas.drawText("Cleaned in $time", w / 2, ty + w * 0.11f, t)

        if (isShown) postInvalidateOnAnimation()
    }
}
