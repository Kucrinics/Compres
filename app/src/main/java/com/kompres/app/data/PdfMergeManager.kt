package com.kompres.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

class PdfMergeManager(private val context: Context) {

    suspend fun mergePdfs(
        inputs: List<Uri>,
        output: Uri,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val streams = mutableListOf<InputStream>()
        try {
            inputs.forEach { uri ->
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input")
                streams += stream
            }
            val destination = context.contentResolver.openOutputStream(output)
                ?: throw IOException("Cannot open output")
            destination.use { PdfMergeCore.mergePdfs(streams, it, onProgress) }
        } finally {
            streams.forEach { runCatching { it.close() } }
        }
    }

    suspend fun imagesToPdf(
        inputs: List<Uri>,
        output: Uri,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val destination = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Cannot open output")
        destination.use {
            PdfMergeCore.imagesToPdf(inputs.size, it, onProgress = onProgress) { index ->
                BitmapLoader.load(context, inputs[index], maxSide = 3000)
                    ?: throw IOException("Cannot decode image")
            }
        }
    }
}
