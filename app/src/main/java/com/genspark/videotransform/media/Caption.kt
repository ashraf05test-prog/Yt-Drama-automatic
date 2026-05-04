package com.genspark.videotransform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.genspark.videotransform.data.CaptionOptions
import com.genspark.videotransform.data.CaptionPosition
import com.genspark.videotransform.data.CaptionStyle
import com.genspark.videotransform.data.ExportQuality
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object CaptionBitmapRenderer {
    fun render(
        context: Context,
        options: CaptionOptions,
        quality: ExportQuality,
        tempFileManager: TempFileManager,
    ): File {
        val frameW = if (quality == ExportQuality.PREVIEW) 540 else 1080
        val frameH = if (quality == ExportQuality.PREVIEW) 960 else 1920
        val inner = frameW
        val bitmap = Bitmap.createBitmap(frameW, frameH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val lines = wrapText(options.text.trim(), 22)
        val barHeight = ((frameH - inner) / 2f).toInt()
        val baseSize = if (quality == ExportQuality.PREVIEW)
            max(28f, options.fontSize * 0.6f) else options.fontSize
        val lineHeight = baseSize * 1.35f
        val blockHeight = (lines.size * lineHeight).toInt()
        val offset = max(16, ((barHeight - blockHeight) / 2f).toInt())
        val firstY = if (options.position == CaptionPosition.TOP) offset + baseSize
        else (frameH - barHeight + offset + baseSize)

        val fillColor = when (options.style) {
            CaptionStyle.DRAMATIC -> Color.YELLOW
            CaptionStyle.FIRE -> Color.parseColor("#FF6B00")
            CaptionStyle.MEME -> Color.WHITE
            CaptionStyle.NORMAL -> runCatching { Color.parseColor(options.color) }.getOrDefault(Color.WHITE)
        }
        val strokeColor = when (options.style) {
            CaptionStyle.DRAMATIC -> Color.RED
            else -> Color.BLACK
        }
        val strokeWidth = when (options.style) {
            CaptionStyle.DRAMATIC -> 8f
            CaptionStyle.FIRE -> 7f
            CaptionStyle.MEME -> 10f
            CaptionStyle.NORMAL -> 6f
        }
        val shadowColor = when (options.style) {
            CaptionStyle.DRAMATIC -> Color.argb(229, 0, 0, 0)
            CaptionStyle.FIRE -> Color.parseColor("#8B0000")
            else -> Color.TRANSPARENT
        }
        val shadowOffset = when (options.style) {
            CaptionStyle.DRAMATIC -> 5f
            CaptionStyle.FIRE -> 3f
            else -> 0f
        }

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = baseSize
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        if (shadowColor != Color.TRANSPARENT) {
            val shadowPaint = Paint(basePaint).apply {
                style = Paint.Style.FILL
                color = shadowColor
            }
            lines.forEachIndexed { i, line ->
                val y = firstY + i * lineHeight + shadowOffset
                canvas.drawText(line, frameW / 2f + shadowOffset, y, shadowPaint)
            }
        }

        val strokePaint = Paint(basePaint).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            this.strokeWidth = strokeWidth * 2
        }
        val fillPaint = Paint(basePaint).apply {
            style = Paint.Style.FILL
            color = fillColor
        }

        lines.forEachIndexed { i, line ->
            val y = firstY + i * lineHeight
            canvas.drawText(line, frameW / 2f, y, strokePaint)
            canvas.drawText(line, frameW / 2f, y, fillPaint)
        }

        val out = tempFileManager.file("caption_${System.currentTimeMillis()}.png")
        FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return out
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            current = when {
                current.isBlank() -> word
                current.length + word.length + 1 <= maxChars -> "$current $word"
                else -> {
                    lines += current
                    word
                }
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }
}
