package com.kompres.app.data

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.InputStream
import java.io.OutputStream

object PdfMergeCore {

    const val A4_WIDTH = 595
    const val A4_HEIGHT = 842
    const val PAGE_MARGIN = 24f

    data class PageRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    fun fitRect(
        srcWidth: Int,
        srcHeight: Int,
        pageWidth: Float = A4_WIDTH.toFloat(),
        pageHeight: Float = A4_HEIGHT.toFloat(),
        margin: Float = PAGE_MARGIN
    ): PageRect {
        require(srcWidth > 0 && srcHeight > 0) { "Invalid image size" }
        val availableWidth = (pageWidth - margin * 2f).coerceAtLeast(1f)
        val availableHeight = (pageHeight - margin * 2f).coerceAtLeast(1f)
        val scale = minOf(
            availableWidth / srcWidth.toFloat(),
            availableHeight / srcHeight.toFloat()
        )
        val width = srcWidth * scale
        val height = srcHeight * scale
        val left = (pageWidth - width) / 2f
        val top = (pageHeight - height) / 2f
        return PageRect(left, top, left + width, top + height)
    }

    fun mergePdfs(
        inputs: List<InputStream>,
        output: OutputStream,
        onProgress: (Float) -> Unit = {}
    ) {
        require(inputs.isNotEmpty()) { "No input documents" }
        val documents = mutableListOf<PDDocument>()
        val result = PDDocument()
        try {
            inputs.forEachIndexed { index, input ->
                val document = PDDocument.load(input)
                if (document.isEncrypted) {
                    runCatching { document.setAllSecurityToBeRemoved(true) }
                }
                documents += document
                document.pages.forEach { page -> result.importPage(page) }
                onProgress((index + 1f) / inputs.size)
            }
            result.save(output)
        } finally {
            runCatching { result.close() }
            documents.forEach { runCatching { it.close() } }
        }
    }

    fun imagesToPdf(
        count: Int,
        output: OutputStream,
        pageWidth: Int = A4_WIDTH,
        pageHeight: Int = A4_HEIGHT,
        onProgress: (Float) -> Unit = {},
        provider: (Int) -> Bitmap
    ) {
        require(count > 0) { "No images" }
        val document = PDDocument()
        try {
            repeat(count) { index ->
                val bitmap = provider(index)
                try {
                    val page = PDPage(PDRectangle(pageWidth.toFloat(), pageHeight.toFloat()))
                    document.addPage(page)
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    val rect = fitRect(
                        bitmap.width,
                        bitmap.height,
                        pageWidth.toFloat(),
                        pageHeight.toFloat()
                    )
                    PDPageContentStream(document, page).use { stream ->
                        stream.drawImage(
                            image,
                            rect.left,
                            pageHeight - rect.bottom,
                            rect.width,
                            rect.height
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
                onProgress((index + 1f) / count)
            }
            document.save(output)
        } finally {
            runCatching { document.close() }
        }
    }
}
