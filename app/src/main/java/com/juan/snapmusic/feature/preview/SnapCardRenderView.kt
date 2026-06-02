package com.juan.snapmusic.feature.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

internal data class SnapCardRenderData(
    val title: String,
    val artist: String,
    val artwork: Bitmap?,
)

internal class SnapCardRenderView(
    context: Context,
    private val data: SnapCardRenderData,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headerRect = RectF(0f, 0f, CardSizePx.toFloat(), HeaderHeightPx.toFloat())
    private val artRect = RectF(0f, HeaderHeightPx.toFloat(), CardSizePx.toFloat(), HeaderHeightPx + ArtHeightPx.toFloat())
    private val infoRect = RectF(0f, HeaderHeightPx + ArtHeightPx.toFloat(), CardSizePx.toFloat(), CardSizePx.toFloat())

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(CardBackground)
        drawHeader(canvas)
        drawArtwork(canvas)
        drawInfo(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        paint.shader = null
        paint.color = CardBackground
        canvas.drawRect(headerRect, paint)
        textPaint.shader = null
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = 28f
        textPaint.color = SnapRed
        canvas.drawText("SnapMusic", 40f, 91f, textPaint)

        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.textSize = 14f
        textPaint.color = SoftText
        val label = "CANCIÓN DEL MES"
        val tracking = textPaint.textSize * 0.15f
        val labelWidth = trackedTextWidth(label, textPaint, tracking)
        drawTrackedText(canvas, label, CardSizePx - 40f - labelWidth, 91f, textPaint, tracking)

        paint.color = RuleColor
        canvas.drawRect(0f, HeaderHeightPx - 1f, CardSizePx.toFloat(), HeaderHeightPx.toFloat(), paint)
    }

    private fun drawArtwork(canvas: Canvas) {
        val artwork = data.artwork
        if (artwork != null && !artwork.isRecycled) {
            canvas.drawBitmap(artwork, centerCropSource(artwork), artRect, null)
        } else {
            paint.shader = LinearGradient(
                0f,
                artRect.top,
                CardSizePx.toFloat(),
                artRect.bottom,
                intArrayOf(Color.rgb(0x12, 0x12, 0x12), Color.rgb(0x1B, 0x1B, 0x1B)),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(artRect, paint)
            paint.shader = null
        }
        paint.shader = RadialGradient(
            CardSizePx.toFloat(),
            artRect.top,
            420f,
            intArrayOf(LightLeakRed, Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(artRect, paint)
        paint.shader = null
    }

    private fun drawInfo(canvas: Canvas) {
        paint.color = InfoBackground
        paint.shader = null
        canvas.drawRect(infoRect, paint)

        textPaint.shader = null
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.textSize = 36f
        textPaint.color = TitleText
        val textLeft = 40f
        val textWidth = CardSizePx - 80f
        canvas.drawText(data.title.ellipsize(textPaint, textWidth), textLeft, infoRect.top + 76f, textPaint)

        textPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textPaint.textSize = 20f
        textPaint.color = SoftText
        canvas.drawText(data.artist.ellipsize(textPaint, textWidth), textLeft, infoRect.top + 117f, textPaint)
        drawWaveform(canvas, infoRect.top + 200f)

        paint.shader = LinearGradient(
            0f,
            CardSizePx - 20f,
            0f,
            CardSizePx.toFloat(),
            intArrayOf(Color.TRANSPARENT, BottomRed),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, CardSizePx - 20f, CardSizePx.toFloat(), CardSizePx.toFloat(), paint)
        paint.shader = null
    }

    private fun drawWaveform(canvas: Canvas, centerY: Float) {
        val totalWidth = Bars * BarWidthPx + (Bars - 1) * BarGapPx
        var x = (CardSizePx - totalWidth) / 2f
        val center = (Bars - 1) / 2f
        paint.color = SnapRed
        paint.shader = null
        repeat(Bars) { index ->
            val distance = abs(index - center) / center
            val envelope = 1f - distance
            val wave = 0.52f + 0.48f * abs(sin(index * 0.72f + 0.35f))
            val height = (5f + 23f * envelope * wave).coerceIn(4f, 28f)
            val rect = RectF(x, centerY - height / 2f, x + BarWidthPx, centerY + height / 2f)
            canvas.drawRoundRect(rect, 2f, 2f, paint)
            x += BarWidthPx + BarGapPx
        }
    }

    private fun centerCropSource(bitmap: Bitmap): Rect {
        val width = bitmap.width
        val height = bitmap.height
        val targetRatio = artRect.width() / artRect.height()
        val sourceRatio = width.toFloat() / height.toFloat()
        return if (sourceRatio > targetRatio) {
            val cropWidth = (height * targetRatio).toInt()
            val left = (width - cropWidth) / 2
            Rect(left, 0, left + cropWidth, height)
        } else {
            val cropHeight = (width / targetRatio).toInt()
            val top = (height - cropHeight) / 2
            Rect(0, top, width, top + cropHeight)
        }
    }

    private fun String.ellipsize(paint: TextPaint, width: Float): String {
        return TextUtils.ellipsize(this, paint, width, TextUtils.TruncateAt.END).toString()
    }

    private fun trackedTextWidth(text: String, paint: Paint, tracking: Float): Float {
        return text.sumOf { char -> paint.measureText(char.toString()).toDouble() }.toFloat() +
            tracking * (text.length - 1).coerceAtLeast(0)
    }

    private fun drawTrackedText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, tracking: Float) {
        var cursor = x
        text.forEach { char ->
            val value = char.toString()
            canvas.drawText(value, cursor, y, paint)
            cursor += paint.measureText(value) + tracking
        }
    }

    companion object {
        const val CardSizePx = 1080
        private const val HeaderHeightPx = 162
        private const val ArtHeightPx = 594
        private const val Bars = 48
        private const val BarWidthPx = 3f
        private const val BarGapPx = 2f
        private const val CardBackground = 0xFF050505.toInt()
        private const val InfoBackground = 0xFF101010.toInt()
        private const val SnapRed = 0xFFFF3131.toInt()
        private const val TitleText = 0xFFF8F8F8.toInt()
        private const val SoftText = 0xFFB8B8B8.toInt()
        private const val RuleColor = 0x26FFFFFF
        private const val LightLeakRed = 0x14FF3131
        private const val BottomRed = 0x0AFF3131
    }
}
