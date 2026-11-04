package com.kompres.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ModelDownloaderHashTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun sha256MatchesReferenceImplementation() {
        val file = folder.newFile("data.bin")
        file.writeBytes(ByteArray(100_000) { (it % 251).toByte() })

        val expected = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

        assertEquals(expected, ModelDownloader.sha256(file))
    }

    @Test
    fun configuredHashesAreValidSha256() {
        assertTrue(OcrModels.hashes.isNotEmpty())
        OcrModels.hashes.values.forEach { hash ->
            assertEquals(64, hash.length)
            assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        }
    }

    @Test
    fun ocrLanguagesCoverBothModels() {
        assertEquals(setOf("rus", "eng"), OcrModels.hashes.keys)
        assertEquals(OcrModels.hashes.keys, OcrModels.sizes.keys)
        assertEquals("rus+eng", OcrModels.COMBINED)
    }
}
