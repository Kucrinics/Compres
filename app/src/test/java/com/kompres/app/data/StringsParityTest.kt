package com.kompres.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies that every translated locale mirrors the default strings.xml:
 * same keys, non-blank values and identical format placeholders.
 */
class StringsParityTest {

    private val resDir: File = sequenceOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }

    private val base: Map<String, String> = readStrings(File(resDir, "values/strings.xml"))

    private val localeFiles: List<File> = resDir.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") }
        .map { File(it, "strings.xml") }
        .filter { it.isFile }
        .sortedBy { it.parentFile.name }

    @Test
    fun allTranslationsMirrorBaseKeys() {
        assertTrue("No translations found", localeFiles.size >= 14)
        localeFiles.forEach { file ->
            val strings = readStrings(file)
            val locale = file.parentFile.name
            assertEquals("Missing keys in $locale", emptySet<String>(), base.keys - strings.keys)
            assertEquals("Extra keys in $locale", emptySet<String>(), strings.keys - base.keys)
        }
    }

    @Test
    fun noTranslationIsBlank() {
        localeFiles.forEach { file ->
            val locale = file.parentFile.name
            readStrings(file).forEach { (key, value) ->
                assertTrue("Blank value for $key in $locale", value.isNotBlank())
            }
        }
    }

    @Test
    fun formatPlaceholdersMatchBase() {
        val expected = base.mapValues { (_, value) -> placeholders(value) }
        localeFiles.forEach { file ->
            val locale = file.parentFile.name
            readStrings(file).forEach { (key, value) ->
                assertEquals("Placeholder mismatch for $key in $locale", expected[key], placeholders(value))
            }
        }
    }

    private fun placeholders(value: String): List<String> =
        PLACEHOLDER.findAll(value).map { it.value }.sorted().toList()

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        val result = LinkedHashMap<String, String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            result[element.getAttribute("name")] = element.textContent.orEmpty()
        }
        return result
    }

    private companion object {
        val PLACEHOLDER = Regex("""%%|%(\d+\$)?[a-zA-Z]""")
    }
}
