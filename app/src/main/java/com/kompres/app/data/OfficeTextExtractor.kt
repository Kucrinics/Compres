package com.kompres.app.data

import java.io.File
import java.util.zip.ZipFile

object OfficeTextExtractor {

    fun extract(file: File, extension: String): String? = when (extension.lowercase()) {
        "docx" -> readTextFromEntry(file, "word/document.xml", arrayOf("</w:p>"))
        "pptx" -> extractPptx(file)
        "xlsx" -> extractXlsx(file)
        "txt", "md", "csv", "log", "json", "xml" -> file.readText()
        else -> null
    }

    private fun extractPptx(file: File): String? = ZipFile(file).use { zip ->
        val slides = zip.entries().toList()
            .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
            .sortedBy { it.name }
        if (slides.isEmpty()) return null
        slides.mapNotNull { entry ->
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
                .let { textFromXml(it, arrayOf("</a:p>")) }
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun extractXlsx(file: File): String? = ZipFile(file).use { zip ->
        val parts = mutableListOf<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val texts = textRuns(xml)
            if (texts.isNotEmpty()) parts.add(texts.joinToString("\n"))
        }
        zip.entries().toList()
            .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
            .sortedBy { it.name }
            .forEach { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                val texts = textRuns(xml)
                if (texts.isNotEmpty()) parts.add(texts.joinToString("\n"))
            }
        parts.joinToString("\n").ifBlank { null }
    }

    private fun textRuns(xml: String): List<String> =
        Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { unescape(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun readTextFromEntry(file: File, entryName: String, breaks: Array<String>): String? =
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryName) ?: return null
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            textFromXml(xml, breaks)
        }

    internal fun textFromXml(xml: String, breaks: Array<String>): String {
        var text = xml
        breaks.forEach { text = text.replace(it, "\n") }
        text = text.replace(Regex("<[^>]+>"), "")
        text = unescape(text)
        return text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun unescape(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
