package com.kompres.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class PdfCompressManager(private val context: Context) {

    suspend fun safeCompress(
        input: Uri,
        output: Uri,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val source = context.contentResolver.openInputStream(input)
            ?: throw IOException("Cannot open input")
        val destination = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Cannot open output")
        source.use {
            destination.use { out ->
                PdfCompressCore.safeCompress(it, out, onProgress)
            }
        }
    }

    suspend fun strongCompress(
        input: Uri,
        output: Uri,
        dpi: Int = 150,
        quality: Int = 75,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val temp = SafHelper.copyToCache(context, input)
        val descriptor = ParcelFileDescriptor.open(
            temp,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
        val renderer = PdfRenderer(descriptor)
        try {
            val count = renderer.pageCount
            if (count == 0) throw IOException("Empty PDF")
            val destination = context.contentResolver.openOutputStream(output)
                ?: throw IOException("Cannot open output")
            destination.use { out ->
                PdfCompressCore.jpegPagesToPdf(
                    count = count,
                    output = out,
                    quality = quality.coerceIn(1, 100) / 100f,
                    onProgress = onProgress
                ) { index ->
                    val page = renderer.openPage(index)
                    try {
                        val (width, height) = PdfCompressCore.renderSize(
                            page.width.toFloat(),
                            page.height.toFloat(),
                            dpi
                        )
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfCompressCore.RenderedPage(
                            bitmap = bitmap,
                            widthPoints = page.width.toFloat(),
                            heightPoints = page.height.toFloat()
                        )
                    } finally {
                        page.close()
                    }
                }
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
            temp.delete()
        }
    }
}
