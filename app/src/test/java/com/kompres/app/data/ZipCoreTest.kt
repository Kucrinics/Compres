package com.kompres.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipCoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun sanitizeRejectsTraversalAndAbsolutePaths() {
        assertNull(ZipCore.sanitize("../evil.txt"))
        assertNull(ZipCore.sanitize("a/../../evil.txt"))
        assertNull(ZipCore.sanitize("/etc/passwd"))
        assertNull(ZipCore.sanitize(""))
        assertEquals("dir/file.txt", ZipCore.sanitize("dir\\file.txt"))
        assertEquals("file.txt", ZipCore.sanitize("./file.txt"))
    }

    @Test
    fun uniqueNameAddsSuffix() {
        val used = mutableSetOf<String>()
        assertEquals("a.txt", ZipCore.uniqueName("a.txt", used))
        assertEquals("a (1).txt", ZipCore.uniqueName("a.txt", used))
        assertEquals("a (2).txt", ZipCore.uniqueName("a.txt", used))
        assertEquals("noext", ZipCore.uniqueName("noext", used))
        assertEquals("noext (1)", ZipCore.uniqueName("noext", used))
    }

    @Test
    fun createListExtractRoundTrip() {
        val zipFile = File(folder.root, "test.zip")
        val sources = listOf(
            ZipCore.Source("one.txt") { ByteArrayInputStream("hello".toByteArray()) },
            ZipCore.Source("dir/two.txt") { ByteArrayInputStream("world!".toByteArray()) }
        )

        zipFile.outputStream().use { out ->
            ZipCore.create(sources, out, 6)
        }

        val entries = ZipCore.list(zipFile)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "one.txt" && !it.isDirectory })
        assertTrue(entries.any { it.name == "dir/two.txt" })

        val dest = File(folder.root, "out")
        val count = ZipCore.extract(zipFile, dest)

        assertEquals(2, count)
        assertEquals("hello", File(dest, "one.txt").readText())
        assertEquals("world!", File(dest, "dir/two.txt").readText())
    }

    @Test
    fun extractSelectedOnlyWritesChosenEntries() {
        val zipFile = File(folder.root, "sel.zip")
        val sources = listOf(
            ZipCore.Source("one.txt") { ByteArrayInputStream("1".toByteArray()) },
            ZipCore.Source("two.txt") { ByteArrayInputStream("2".toByteArray()) }
        )
        zipFile.outputStream().use { out -> ZipCore.create(sources, out, 6) }

        val dest = File(folder.root, "selOut")
        val count = ZipCore.extract(zipFile, dest, setOf("two.txt"))

        assertEquals(1, count)
        assertFalse(File(dest, "one.txt").exists())
        assertEquals("2", File(dest, "two.txt").readText())
    }

    @Test
    fun extractBlocksZipSlipEntries() {
        val zipFile = File(folder.root, "evil.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("ok.txt"))
            zip.write("good".toByteArray())
            zip.closeEntry()
        }

        val dest = File(folder.root, "safe/root")
        dest.mkdirs()
        val count = ZipCore.extract(zipFile, dest)

        assertEquals(1, count)
        assertFalse(File(folder.root, "safe/escape.txt").exists())
        assertFalse(File(folder.root, "escape.txt").exists())
        assertTrue(File(dest, "ok.txt").exists())
    }
}
