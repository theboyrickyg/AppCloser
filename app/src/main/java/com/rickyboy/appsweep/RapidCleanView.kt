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
    private val sweepPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.ADD)
    }
    private val hotBitmapShader = android.graphics.BitmapShader(
        BitmapFactory.decodeResource(resources, R.drawable.horns_hot_only, BitmapFactory.Options().apply { inScaled = false }),
        android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
    private val shaderMatrix = android.graphics.Matrix()

    /** The glowing-horn picture as a paint, placed where the ram is drawn. */
    private fun hotShader(s: Float, ox: Float, oy: Float): android.graphics.Shader {
        shaderMatrix.setScale(s, s)
        shaderMatrix.postTranslate(ox, oy)
        hotBitmapShader.setLocalMatrix(shaderMatrix)
        return hotBitmapShader
    }
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
        // fire-like flicker: a few uneven waves added together
        val flicker = (0.78f + 0.10f * sin(now / 170f) + 0.07f * sin(now / 61f + 1.3f) + 0.05f * sin(now / 23f + 0.4f))
            .coerceIn(0f, 1f)
        val pad = (halo.width - ram.width) / 2f
        // the halo breathes in and out as well as flickering
        val grow = 1f + 0.05f * sin(now / 420f)
        val cx = ox + ram.width * s / 2
        val cy = oy + ram.height * s / 2
        val hw = (ram.width / 2f + pad) * s * grow
        val hh = (ram.height / 2f + pad) * s * grow
        img.alpha = (heat * flicker * 255).toInt().coerceIn(0, 255)
        r.set(cx - hw, cy - hh, cx + hw, cy + hh)
        canvas.drawBitmap(halo, null, r, img)
        r.set(ox, oy, ox + ram.width * s, oy + ram.height * s)
        img.alpha = 255
        canvas.drawBitmap(ram, null, r, img)
        img.alpha = (heat * (0.65f + 0.30f * flicker) * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(hot, null, r, img)
        img.alpha = 255

        // bright bands of heat sweeping across the horns, like embers glowing hotter and cooler
        if (heat > 0f) {
            val band = (now % 1800L) / 1800f
            val bx = ox - ram.width * s * 0.3f + ram.width * s * 1.6f * band
            sweepPaint.shader = android.graphics.ComposeShader(
                hotShader(s, ox, oy),
                android.graphics.LinearGradient(bx - w * 0.18f, oy, bx + w * 0.18f, oy + ram.height * s,
                    intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP),
                android.graphics.PorterDuff.Mode.DST_IN
            )
            sweepPaint.alpha = (heat * 150).toInt()
            canvas.drawRect(r, sweepPaint)
            sweepPaint.shader = null
        }
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
