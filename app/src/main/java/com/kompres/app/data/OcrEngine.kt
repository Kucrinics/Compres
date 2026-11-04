package com.kompres.app.data

import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class OcrEngine(context: android.content.Context) {

    private val dataPath = File(context.filesDir, "tesseract").absolutePath

    suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.Default) {
        val tess = TessBaseAPI()
        try {
            if (!tess.init(dataPath, OcrModels.COMBINED)) {
                throw IllegalStateException("Tesseract initialization failed")
            }
            tess.setImage(bitmap)
            tess.getUTF8Text() ?: ""
        } finally {
            tess.recycle()
        }
    }
}
