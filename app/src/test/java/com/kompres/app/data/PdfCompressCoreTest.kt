package com.kompres.app.data

import android.graphics.Bitmap
import android.graphics.Color
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfCompressCoreTest {

    @Test
    fun renderSizeAt150Dpi() {
        assertEquals(1240 to 1754, PdfCompressCore.renderSize(595f, 842f, 150))
        assertEquals(612 to 792, PdfCompressCore.renderSize(612f, 792f, 72))
    }

    @Test
    fun renderSizeNeverZero() {
        assertEquals(1 to 1, PdfCompressCore.renderSize(0f, 0f, 150))
    }

    @Test
    fun safeCompressKeepsPages() {
        val input = createPdf(2)
        val output = ByteArrayOutputStream()
        var progress = 0f

        PdfCompressCore.safeCompress(ByteArrayInputStream(input), output) { progress = it }

        assertEquals(1f, progress, 0.001f)
        PDDocument.load(ByteArrayInputStream(output.toByteArray())).use { document ->
            assertEquals(2, document.numberOfPages)
        }
    }

    @Test
    fun jpegPagesKeepPageSizes() {
        val output = ByteArrayOutputStream()

        PdfCompressCore.jpegPagesToPdf(
            count = 2,
            output = output,
            quality = 0.75f
        ) { index ->
            val bitmap = Bitmap.createBitmap(150, 200, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (index == 0) Color.GREEN else Color.YELLOW)
            }
            PdfCompressCore.RenderedPage(bitmap, 300f, 400f)
        }

        assertTrue(output.size() > 0)
        PDDocument.load(ByteArrayInputStream(output.toByteArray())).use { document ->
            assertEquals(2, document.numberOfPages)
            val box = document.getPage(0).mediaBox
            assertEquals(300f, box.width, 0.5f)
            assertEquals(400f, box.height, 0.5f)
        }
    }

    private fun createPdf(pages: Int): ByteArray {
        val document = PDDocument()
        repeat(pages) {
            document.addPage(PDPage(PDRectangle.A4))
        }
        val output = ByteArrayOutputStream()
        document.save(output)
        document.close()
        return output.toByteArray()
    }
}
