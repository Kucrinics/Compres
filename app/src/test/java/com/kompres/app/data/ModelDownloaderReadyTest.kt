package com.kompres.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ModelDownloaderReadyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory: File
        get() = File(context.filesDir, "tesseract/tessdata")

    @After
    fun cleanup() {
        File(context.filesDir, "tesseract").deleteRecursively()
    }

    @Test
    fun notReadyWithoutModels() {
        assertFalse(ModelDownloader(context).isReady())
    }

    @Test
    fun notReadyWithCorruptedModels() {
        directory.mkdirs()
        OcrModels.languages.forEach { language ->
            File(directory, "$language.traineddata")
                .writeBytes(ByteArray(1024) { 1 })
        }
        assertFalse(ModelDownloader(context).isReady())
    }
}
