package com.kompres.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri

object BitmapLoader {

    fun load(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            var sample = 1
            while (maxOf(info.size.width, info.size.height) / (sample * 2) >= maxSide) {
                sample *= 2
            }
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
}
