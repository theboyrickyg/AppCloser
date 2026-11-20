package com.rickyboy.appsweep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View

/**
 * An illustrated theme ("skin"): pictures for the background, the top picture with an empty
 * dial, the two big buttons, the app list frame, the two little tags and the close button.
 * The app writes the live numbers and words on top.
 */
data class Skin(
    val background: Int,
    val header: Int,
    val dialX: Float, val dialY: Float, val dialR: Float,   // empty dial: centre and radius, as fractions of the header's width/height/width
    val basic: Int,
    val rapid: Int,
    val list: Int,
    val tagFree: Int,
    val tagApps: Int,
    val close: Int,
    val tagFreeText: Int,
    val tagAppsText: Int,
    val dialText: Int,
    val dialLabel: Int
)

/** The top picture with the live "71% RAM USED" written into its empty dial. */
class SkinHeaderView(context: Context, private val skin: Skin) : View(context) {
    private val pic: Bitmap = BitmapFactory.decodeResource(resources, skin.header,
        BitmapFactory.Options().apply { inScaled = false })
    private val img = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val dst = RectF()

    var percent = 0f
        set(v) { field = v; invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w.toFloat() * pic.height / pic.width).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        dst.set(0f, 0f, w, h)
        canvas.drawBitmap(pic, null, dst, img)
        val cx = w * skin.dialX
        val cy = h * skin.dialY
        val r = w * skin.dialR
        big.textSize = r * 0.62f
        big.color = skin.dialText
        big.setShadowLayer(r * 0.06f, 0f, r * 0.02f, Color.BLACK)
        canvas.drawText("${percent.toInt()}%", cx, cy + r * 0.12f, big)
        small.textSize = r * 0.19f
        small.color = skin.dialLabel
        small.setShadowLayer(r * 0.04f, 0f, 0f, Color.BLACK)
        canvas.drawText("RAM USED", cx, cy + r * 0.48f, small)
    }
}

/**
 * Stretches a framed picture to any height without squashing its decorated top and bottom:
 * the top and bottom bands keep their shape, only the plain middle is stretched.
 */
class FrameDrawable(context: Context, res: Int, private val topShare: Float = 0.30f, private val bottomShare: Float = 0.22f) : Drawable() {
    private val pic: Bitmap = BitmapFactory.decodeResource(context.resources, res,
        BitmapFactory.Options().apply { inScaled = false })
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val scale = b.width().toFloat() / pic.width
        val topSrc = (pic.height * topShare).toInt()
        val botSrc = (pic.height * bottomShare).toInt()
        val topDst = topSrc * scale
        val botDst = botSrc * scale
        // top band
        src.set(0, 0, pic.width, topSrc)
        dst.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + topDst)
        canvas.drawBitmap(pic, src, dst, paint)
        // stretched middle
        src.set(0, topSrc, pic.width, pic.height - botSrc)
        dst.set(b.left.toFloat(), b.top + topDst, b.right.toFloat(), b.bottom - botDst)
        if (dst.height() > 0) canvas.drawBitmap(pic, src, dst, paint)
        // bottom band
        src.set(0, pic.height - botSrc, pic.width, pic.height)
        dst.set(b.left.toFloat(), b.bottom - botDst, b.right.toFloat(), b.bottom.toFloat())
        canvas.drawBitmap(pic, src, dst, paint)
    }

    override fun getPadding(padding: Rect): Boolean = false
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
