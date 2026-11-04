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
class PdfMergeCorePdfTest {

    @Test
    fun mergeCombinesPages() {
        val first = createPdf(1)
        val second = createPdf(2)
        val output = ByteArrayOutputStream()

        PdfMergeCore.mergePdfs(
            listOf(ByteArrayInputStream(first), ByteArrayInputStream(second)),
            output
        )

        assertTrue(output.size() > 0)
        PDDocument.load(ByteArrayInputStream(output.toByteArray())).use { merged ->
            assertEquals(3, merged.numberOfPages)
        }
    }

    @Test
    fun imagesToPdfUsesA4Pages() {
        val output = ByteArrayOutputStream()

        PdfMergeCore.imagesToPdf(
            count = 2,
            output = output
        ) { index ->
            Bitmap.createBitmap(200 + index, 100, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (index == 0) Color.RED else Color.BLUE)
            }
        }

        assertTrue(output.size() > 0)
        PDDocument.load(ByteArrayInputStream(output.toByteArray())).use { document ->
            assertEquals(2, document.numberOfPages)
            val box = document.getPage(0).mediaBox
            assertEquals(PdfMergeCore.A4_WIDTH.toFloat(), box.width, 0.5f)
            assertEquals(PdfMergeCore.A4_HEIGHT.toFloat(), box.height, 0.5f)
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
