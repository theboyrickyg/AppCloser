package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * The cleaning screen's moving picture: an app slides in, the ram pulls its head back,
 * then butts it with its horns. The app goes flying. Loops until
 * the view is removed.
 */
class HeadbuttRamView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val W = 900f            // drawing space; scaled to the view's width
        const val H = 540f
        const val CYCLE_MS = 1150L    // one headbutt
        const val HEAD_H = 400f       // the head's height in drawing space
        const val REST_X = 70f
        const val REST_Y = 40f
        // points on the head picture (its own pixels)
        const val NECK_X = 330f       // the head pivots from the neck
        const val NECK_Y = 640f
        const val HIT_X = 565f        // the crown of the horns, where it strikes
        const val HIT_Y = 50f
        const val ICON = 104f         // app icon size
        const val IMPACT = 0.54f      // when in the cycle the horns connect
    }

    private val head: Bitmap
    private val hs: Float
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fx = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = SystemClock.uptimeMillis()
    private val m = Matrix()
    private val pt = FloatArray(2)
    private val strike = FloatArray(2)

    // Five app icons, in the same colours as the rest of App Closer's artwork
    private val iconColors = arrayOf(
        intArrayOf(0xFFFFC52E.toInt(), 0xFFF29312.toInt()),   // sun
        intArrayOf(0xFFF5527F.toInt(), 0xFFD11A56.toInt()),   // heart
        intArrayOf(0xFFA066F2.toInt(), 0xFF6A30C4.toInt()),   // star
        intArrayOf(0xFF58A9FF.toInt(), 0xFF1D6BDB.toInt()),   // cloud
        intArrayOf(0xFF2DCDBB.toInt(), 0xFF0F9184.toInt())    // moon
    )
    private val glyphs = Array(5) { Path() }
    private val iconRect = RectF()

    init {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        head = BitmapFactory.decodeResource(resources, R.drawable.headbutt_ram, opts)
        hs = HEAD_H / head.height
        buildGlyphs()
        // where the horns end up at the moment of impact: the app waits there
        pose(28f, 75f)
        pt[0] = HIT_X; pt[1] = HIT_Y
        m.mapPoints(strike, pt)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, (width * H / W).toInt())
    }

    private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The head's position: tilt in degrees (+ = nose down, horns forward) and push forward. */
    private fun pose(rot: Float, dx: Float) {
        m.setScale(hs, hs)
        m.postTranslate(REST_X, REST_Y)
        m.postRotate(rot, REST_X + NECK_X * hs, REST_Y + NECK_Y * hs)
        m.postTranslate(dx, 0f)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / W
        val elapsed = SystemClock.uptimeMillis() - start
        val u = (elapsed % CYCLE_MS) / CYCLE_MS.toFloat()
        val which = ((elapsed / CYCLE_MS) % iconColors.size).toInt()

        // Head: idle, pull back, butt, hold, settle
        val (rot, dx) = when {
            u < 0.20f -> Pair(1.5f * sin(u * 30f), 0f)
            u < 0.46f -> smooth((u - 0.20f) / 0.26f).let { Pair(-10f * it, -30f * it) }
            u < IMPACT -> ((u - 0.46f) / (IMPACT - 0.46f)).let { t -> val e = t * t; Pair(lerp(-10f, 28f, e), lerp(-30f, 75f, e)) }
            u < 0.64f -> ((u - IMPACT) / 0.10f).let { Pair(lerp(28f, 22f, it), lerp(75f, 62f, it)) }
            else -> smooth((u - 0.64f) / 0.36f).let { Pair(lerp(22f, 0f, it), lerp(62f, 0f, it)) }
        }
        val k = if (u >= IMPACT) (u - IMPACT) / (1f - IMPACT) else -1f

        canvas.save()
        canvas.scale(s, s)
        // a little jolt on impact
        if (k in 0f..0.12f) {
            val f = 1f - k / 0.12f
            canvas.translate(sin(k * 220f) * 7f * f, cos(k * 260f) * 4f * f)
        }

        pose(rot, dx)
        canvas.drawBitmap(head, m, paint)

        val waitX = strike[0] + ICON / 2 + 2f
        val waitY = strike[1] + 6f
        if (k < 0f) {
            // the app slides in from the right and trembles while it waits
            val inT = smooth(u / 0.40f)
            val x = lerp(W + ICON, waitX, inT)
            val tremble = if (u > 0.40f) sin(u * 140f) * 4f else 0f
            drawIcon(canvas, which, x, waitY + sin(u * 9f) * 3f, tremble, 1f, 255)
        } else {
            // hit: the app spins away and fades
            val x = waitX + 900f * k
            val y = waitY - 430f * k + 320f * k * k
            val alpha = if (k < 0.45f) 255 else (255 * (1f - (k - 0.45f) / 0.55f)).toInt()
            drawIcon(canvas, which, x, y, 720f * k, 1f - 0.35f * k, alpha.coerceIn(0, 255))
            if (k < 0.25f) drawBurst(canvas, strike[0] + 8f, strike[1], k / 0.25f)
        }
        canvas.restore()
        if (isShown) postInvalidateOnAnimation()
    }

    /** Comic "pow" lines at the point of impact. */
    private fun drawBurst(canvas: Canvas, cx: Float, cy: Float, t: Float) {
        val a = ((1f - t) * 255).toInt()
        fx.style = Paint.Style.STROKE
        fx.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 10) {
            val ang = (i * 36f + 8f) * Math.PI.toFloat() / 180f
            val r0 = 18f + 40f * t
            val r1 = r0 + 34f + 50f * (1f - t)
            fx.strokeWidth = if (i % 2 == 0) 7f else 4f
            fx.color = if (i % 2 == 0) Color.argb(a, 255, 224, 130) else Color.argb(a, 255, 255, 255)
            canvas.drawLine(cx + cos(ang) * r0, cy + sin(ang) * r0, cx + cos(ang) * r1, cy + sin(ang) * r1, fx)
        }
        fx.strokeWidth = 5f
        fx.color = Color.argb(a / 2, 255, 255, 255)
        canvas.drawCircle(cx, cy, 20f + 70f * t, fx)
        fx.style = Paint.Style.FILL
        fx.color = Color.WHITE
    }

    private fun drawIcon(canvas: Canvas, i: Int, cx: Float, cy: Float, deg: Float, scale: Float, alpha: Int) {
        if (alpha <= 0) return
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(deg)
        canvas.scale(scale, scale)
        val h = ICON / 2
        val r = ICON * 0.24f
        // black comic outline
        fx.shader = null
        fx.style = Paint.Style.FILL
        fx.color = Color.argb(alpha, 10, 10, 14)
        iconRect.set(-h - 5f, -h - 5f, h + 5f, h + 5f)
        canvas.drawRoundRect(iconRect, r + 5f, r + 5f, fx)
        // coloured tile with a soft top-to-bottom shade
        fx.shader = LinearGradient(0f, -h, 0f, h, iconColors[i][0], iconColors[i][1], Shader.TileMode.CLAMP)
        fx.alpha = alpha
        iconRect.set(-h, -h, h, h)
        canvas.drawRoundRect(iconRect, r, r, fx)
        fx.shader = null
        // shine on the top half
        fx.color = Color.argb(alpha * 45 / 255, 255, 255, 255)
        iconRect.set(-h + 6f, -h + 6f, h - 6f, -2f)
        canvas.drawRoundRect(iconRect, r - 4f, r - 4f, fx)
        // white symbol
        fx.color = Color.argb(alpha, 255, 255, 255)
        canvas.save()
        canvas.scale(ICON * 0.32f, ICON * 0.32f)
        canvas.drawPath(glyphs[i], fx)
        canvas.restore()
        canvas.restore()
    }

    /** Simple white symbols, drawn in a -1..1 box. */
    private fun buildGlyphs() {
        // sun: disc and eight rays
        glyphs[0].apply {
            addCircle(0f, 0f, 0.48f, Path.Direction.CW)
            for (k in 0 until 8) {
                val ray = Path()
                ray.addRoundRect(RectF(-0.09f, -1.0f, 0.09f, -0.66f), 0.08f, 0.08f, Path.Direction.CW)
                val rm = Matrix(); rm.setRotate(k * 45f)
                ray.transform(rm)
                addPath(ray)
            }
        }
        // heart
        glyphs[1].apply {
            moveTo(0f, 0.85f)
            cubicTo(-1.25f, 0.0f, -0.8f, -1.0f, 0f, -0.45f)
            cubicTo(0.8f, -1.0f, 1.25f, 0.0f, 0f, 0.85f)
            close()
        }
        // star
        glyphs[2].apply {
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) 0.95f else 0.42f
                val a = -Math.PI / 2 + k * Math.PI / 5
                val x = (cos(a) * r).toFloat(); val y = (sin(a) * r).toFloat() + 0.06f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        // cloud
        glyphs[3].apply {
            addCircle(-0.42f, 0.18f, 0.36f, Path.Direction.CW)
            addCircle(0.05f, -0.12f, 0.5f, Path.Direction.CW)
            addCircle(0.5f, 0.2f, 0.34f, Path.Direction.CW)
            addRoundRect(RectF(-0.78f, 0.1f, 0.84f, 0.54f), 0.22f, 0.22f, Path.Direction.CW)
        }
        // moon: a disc with a bite out of it
        glyphs[4].apply {
            val disc = Path().apply { addCircle(0f, 0f, 0.78f, Path.Direction.CW) }
            val bite = Path().apply { addCircle(0.38f, -0.28f, 0.66f, Path.Direction.CW) }
            disc.op(bite, Path.Op.DIFFERENCE)
            addPath(disc)
        }
        // overlapping shapes fill as one
        glyphs.forEach { it.fillType = Path.FillType.WINDING }
    }
}
