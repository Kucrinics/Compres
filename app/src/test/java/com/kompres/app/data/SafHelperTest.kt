package com.kompres.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafHelperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun openDocumentsPassesMimeTypesAndMultiplicity() {
        val intent = SafHelper.openDocuments(arrayOf("application/pdf"), multiple = true)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertArrayEquals(
            arrayOf("application/pdf"),
            intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        )
    }

    @Test
    fun createDocumentSetsMimeTypeAndTitle() {
        val intent = SafHelper.createDocument("test.zip", "application/zip")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals("test.zip", intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun fileDisplayNameAndSize() {
        val file = File.createTempFile("photo", ".jpg")
        file.writeBytes(ByteArray(5))
        try {
            val uri = Uri.fromFile(file)
            assertTrue(SafHelper.displayName(context, uri).endsWith(file.name))
            assertEquals(5L, SafHelper.size(context, uri))
        } finally {
            file.delete()
        }
    }
}
