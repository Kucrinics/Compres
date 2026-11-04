package com.kompres.app.data

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

object PdfCompressCore {

    data class RenderedPage(
        val bitmap: Bitmap,
        val widthPoints: Float,
        val heightPoints: Float
    )

    fun renderSize(widthPoints: Float, heightPoints: Float, dpi: Int = 150): Pair<Int, Int> {
        val width = (widthPoints / 72f * dpi).roundToInt().coerceAtLeast(1)
        val height = (heightPoints / 72f * dpi).roundToInt().coerceAtLeast(1)
        return width to height
    }

    fun safeCompress(
        input: InputStream,
        output: OutputStream,
        onProgress: (Float) -> Unit = {}
    ) {
        val document = PDDocument.load(input)
        try {
            if (document.isEncrypted) {
                runCatching { document.setAllSecurityToBeRemoved(true) }
            }
            onProgress(0.5f)
            document.save(output)
            onProgress(1f)
        } finally {
            runCatching { document.close() }
        }
    }

    fun jpegPagesToPdf(
        count: Int,
        output: OutputStream,
        quality: Float = 0.75f,
        onProgress: (Float) -> Unit = {},
        provider: (Int) -> RenderedPage
    ) {
        require(count > 0) { "No pages" }
        val document = PDDocument()
        try {
            repeat(count) { index ->
                val page = provider(index)
                try {
                    val pdfPage = PDPage(PDRectangle(page.widthPoints, page.heightPoints))
                    document.addPage(pdfPage)
                    val image = JPEGFactory.createFromImage(
                        document,
                        page.bitmap,
                        quality.coerceIn(0f, 1f)
                    )
                    PDPageContentStream(document, pdfPage).use { stream ->
                        stream.drawImage(
                            image,
                            0f,
                            0f,
                            page.widthPoints,
                            page.heightPoints
                        )
                    }
                } finally {
                    page.bitmap.recycle()
                }
                onProgress((index + 1f) / count)
            }
            document.save(output)
        } finally {
            runCatching { document.close() }
        }
    }
}
