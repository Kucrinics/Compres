package com.kompres.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

enum class ImageFormat(val mime: String, val extension: String, val lossy: Boolean) {
    JPG("image/jpeg", "jpg", true),
    PNG("image/png", "png", false),
    WEBP("image/webp", "webp", true),
    WEBP_LOSSLESS("image/webp", "webp", false),
    JFIF("image/jpeg", "jfif", true);

    val displayName: String
        get() = when (this) {
            JPG -> "JPG"
            PNG -> "PNG"
            WEBP -> "WebP"
            WEBP_LOSSLESS -> "WebP lossless"
            JFIF -> "JFIF"
        }

    val compressFormat: Bitmap.CompressFormat
        get() = when (this) {
            PNG -> Bitmap.CompressFormat.PNG
            WEBP, WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (this == WEBP_LOSSLESS) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP_LOSSY
                }
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            JPG, JFIF -> Bitmap.CompressFormat.JPEG
        }
}

data class ImageOptions(
    val format: ImageFormat = ImageFormat.JPG,
    val quality: Int = 85,
    val scalePercent: Int = 100,
    val cropRatio: Float? = null
)

object ImageCore {

    data class Result(val width: Int, val height: Int)

    fun convertToStream(
        input: InputStream,
        output: OutputStream,
        options: ImageOptions
    ): Result {
        val bytes = input.readBytes()
        val rotation = exifRotation(bytes)
        var bitmap = decode(bytes)
        try {
            if (rotation != 0) {
                bitmap = bitmap.replacedBy(rotate(bitmap, rotation))
            }
            options.cropRatio?.let { ratio ->
                if (ratio > 0f) bitmap = bitmap.replacedBy(centerCrop(bitmap, ratio))
            }
            if (options.scalePercent in 1..99) {
                bitmap = bitmap.replacedBy(scale(bitmap, options.scalePercent))
            }
            val quality = if (options.format.lossy) options.quality.coerceIn(0, 100) else 100
            bitmap.compress(options.format.compressFormat, quality, output)
            return Result(bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /** Recycles the intermediate bitmap when a transformation produced a new one. */
    private fun Bitmap.replacedBy(next: Bitmap): Bitmap {
        if (next !== this) recycle()
        return next
    }

    fun centerCropRect(width: Int, height: Int, ratio: Float): Rect {
        val current = width.toFloat() / height.toFloat()
        return if (current > ratio) {
            val newWidth = (height * ratio).roundToInt().coerceAtLeast(1)
            val left = (width - newWidth) / 2
            Rect(left, 0, left + newWidth, height)
        } else {
            val newHeight = (width / ratio).roundToInt().coerceAtLeast(1)
            val top = (height - newHeight) / 2
            Rect(0, top, width, top + newHeight)
        }
    }

    fun scaledSize(width: Int, height: Int, percent: Int): Pair<Int, Int> {
        val w = (width * percent / 100f).roundToInt().coerceAtLeast(1)
        val h = (height * percent / 100f).roundToInt().coerceAtLeast(1)
        return w to h
    }

    private fun centerCrop(bitmap: Bitmap, ratio: Float): Bitmap {
        val rect = centerCropRect(bitmap.width, bitmap.height, ratio)
        if (rect.width() == bitmap.width && rect.height() == bitmap.height) return bitmap
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }

    private fun scale(bitmap: Bitmap, percent: Int): Bitmap {
        val (w, h) = scaledSize(bitmap.width, bitmap.height, percent)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / (sample * 2) >= MAX_DECODE_SIDE) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IllegalArgumentException("Cannot decode image")
    }

    private fun exifRotation(bytes: ByteArray): Int = try {
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private const val MAX_DECODE_SIDE = 4096
}
