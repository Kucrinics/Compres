package com.kompres.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageManager(private val context: Context) {

    suspend fun convertToUri(
        input: Uri,
        output: Uri,
        options: ImageOptions
    ): ImageCore.Result = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(input)
            ?: throw IOException("Cannot open input")
        val outputStream = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Cannot open output")
        inputStream.use { source ->
            outputStream.use { destination ->
                ImageCore.convertToStream(source, destination, options)
            }
        }
    }

    suspend fun convertToTree(
        inputs: List<Uri>,
        destinationTree: Uri,
        options: ImageOptions,
        onProgress: (Float) -> Unit = {}
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, destinationTree)
            ?: throw IOException("Cannot open destination tree")
        var success = 0
        var failed = 0
        inputs.forEachIndexed { index, uri ->
            try {
                val name = SafHelper.displayName(context, uri)
                val base = name.substringBeforeLast('.', name).ifBlank { "image" }
                val targetName = uniqueTreeName(root, "${base}_converted.${options.format.extension}")
                val document = root.createFile(options.format.mime, targetName)
                    ?: throw IOException("Cannot create file")
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input")
                val outputStream = context.contentResolver.openOutputStream(document.uri)
                    ?: throw IOException("Cannot open output")
                inputStream.use { source ->
                    outputStream.use { destination ->
                        ImageCore.convertToStream(source, destination, options)
                    }
                }
                success++
            } catch (_: Exception) {
                failed++
            }
            onProgress((index + 1f) / inputs.size)
        }
        success to failed
    }

    private fun uniqueTreeName(root: DocumentFile, name: String): String {
        if (root.findFile(name) == null) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var counter = 1
        while (true) {
            val candidate = "$base ($counter)$ext"
            if (root.findFile(candidate) == null) return candidate
            counter++
        }
    }
}
