package com.kompres.app.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfficeTextExtractorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun zipFile(name: String, entries: Map<String, String>): File {
        val file = File(folder.root, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun extractsDocxText() {
        val file = zipFile(
            "doc.docx",
            mapOf(
                "word/document.xml" to
                    "<w:document><w:body>" +
                    "<w:p><w:r><w:t>Hello</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>World &amp; more</w:t></w:r></w:p>" +
                    "</w:body></w:document>"
            )
        )

        val text = OfficeTextExtractor.extract(file, "docx")

        assertTrue(text!!.contains("Hello"))
        assertTrue(text.contains("World & more"))
    }

    @Test
    fun extractsPptxSlides() {
        val file = zipFile(
            "slides.pptx",
            mapOf(
                "ppt/slides/slide1.xml" to "<p:sld><a:p><a:t>First slide</a:t></a:p></p:sld>",
                "ppt/slides/slide2.xml" to "<p:sld><a:p><a:t>Second slide</a:t></a:p></p:sld>"
            )
        )

        val text = OfficeTextExtractor.extract(file, "pptx")

        assertTrue(text!!.contains("First slide"))
        assertTrue(text.contains("Second slide"))
    }

    @Test
    fun extractsXlsxSharedStrings() {
        val file = zipFile(
            "sheet.xlsx",
            mapOf(
                "xl/sharedStrings.xml" to
                    "<sst><si><t>Alpha</t></si><si><t>Beta</t></si></sst>"
            )
        )

        val text = OfficeTextExtractor.extract(file, "xlsx")

        assertTrue(text!!.contains("Alpha"))
        assertTrue(text.contains("Beta"))
    }

    @Test
    fun readsPlainText() {
        val file = File(folder.root, "note.txt").apply { writeText("plain text") }
        assertTrue(OfficeTextExtractor.extract(file, "txt")!!.contains("plain text"))
    }

    @Test
    fun returnsNullForUnsupported() {
        val file = File(folder.root, "old.doc").apply { writeText("legacy") }
        assertNull(OfficeTextExtractor.extract(file, "doc"))
    }

    @Test
    fun textFromXmlStripsTagsAndUnescapes() {
        val xml = "<a><b>One</b></a></p><a><b>Two &lt;3</b></a>"
        val text = OfficeTextExtractor.textFromXml(xml, arrayOf("</p>"))
        assertTrue(text.contains("One"))
        assertTrue(text.contains("Two <3"))
    }
}
