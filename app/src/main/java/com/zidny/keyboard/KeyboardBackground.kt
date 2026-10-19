package com.zidny.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File
import kotlin.math.max

/** The gallery photo behind the keys: picked in the settings, saved small, drawn blurred and darkened. */
object KeyboardBackground {

    private const val SAVED_WIDTH = 1080

    fun file(context: Context) = File(context.filesDir, KeyboardSettings.BACKGROUND_FILE)

    /** Copies the photo at [uri] into the app at a size a keyboard needs. Returns false if it can't be read. */
    fun save(context: Context, uri: Uri): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SAVED_WIDTH) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bitmap == null) {
            false
        } else {
            val temp = File(context.filesDir, KeyboardSettings.BACKGROUND_FILE + ".tmp")
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            temp.renameTo(file(context))
        }
    } catch (e: Exception) {
        false
    }

    /**
     * The saved photo as a drawable, or null when there is none. Blur comes from shrinking the photo and
     * letting it be stretched back smoothly: cheap, and good enough behind keys.
     */
    fun load(context: Context, blurPercent: Int, dimPercent: Int): Drawable? {
        val file = file(context)
        if (!file.exists()) return null
        val width = context.resources.displayMetrics.widthPixels
        val target = max(24, width * (100 - blurPercent.coerceIn(0, 100) * 95 / 100) / 100 / 2)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val small = if (decoded.width > target) {
            Bitmap.createScaledBitmap(decoded, target, max(1, decoded.height * target / decoded.width), true)
                .also { if (it != decoded) decoded.recycle() }
        } else decoded
        return CropDrawable(small, Color.argb(dimPercent.coerceIn(0, 90) * 255 / 100, 0, 0, 0))
    }

    /** Fills its bounds with [bitmap] like centerCrop, then lays [shade] over it. */
    private class CropDrawable(private val bitmap: Bitmap, private val shade: Int) : Drawable() {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val matrix = Matrix()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val scale = max(b.width().toFloat() / bitmap.width, b.height().toFloat() / bitmap.height)
            matrix.setScale(scale, scale)
            matrix.postTranslate(b.left + (b.width() - bitmap.width * scale) / 2, b.top + (b.height() - bitmap.height * scale) / 2)
            canvas.save()
            canvas.clipRect(b)
            canvas.drawBitmap(bitmap, matrix, paint)
            canvas.restore()
            canvas.drawColor(shade)
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.OPAQUE
    }
}
