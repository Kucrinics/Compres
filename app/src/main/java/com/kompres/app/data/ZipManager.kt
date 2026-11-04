package com.kompres.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ZipManager(private val context: Context) {

    suspend fun listEntries(zipUri: Uri): List<ZipCore.Entry> = withContext(Dispatchers.IO) {
        val temp = SafHelper.copyToCache(context, zipUri)
        try {
            ZipCore.list(temp)
        } finally {
            temp.delete()
        }
    }

    suspend fun createArchive(
        inputs: List<Uri>,
        output: Uri,
        level: Int,
        onProgress: (Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val sources = inputs.map { uri ->
            val name = SafHelper.displayName(context, uri)
            ZipCore.Source(name) {
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input: $uri")
            }
        }
        val stream = context.contentResolver.openOutputStream(output)
            ?: throw IOException("Cannot open output: $output")
        stream.use {
            ZipCore.create(sources, it, level) { done, total ->
                if (total > 0) onProgress(done.toFloat() / total)
            }
        }
    }

    suspend fun extractAll(
        zipUri: Uri,
        destinationTree: Uri,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        extractInternal(zipUri, destinationTree, null, onProgress)
    }

    suspend fun extractSelected(
        zipUri: Uri,
        destinationTree: Uri,
        names: Set<String>,
        onProgress: (Float) -> Unit = {}
    ): Int = withContext(Dispatchers.IO) {
        extractInternal(zipUri, destinationTree, names, onProgress)
    }

    private fun extractInternal(
        zipUri: Uri,
        destinationTree: Uri,
        names: Set<String>?,
        onProgress: (Float) -> Unit
    ): Int {
        val temp = SafHelper.copyToCache(context, zipUri)
        val workDir = File(context.cacheDir, "unzip_${System.currentTimeMillis()}")
        workDir.mkdirs()
        try {
            val count = ZipCore.extract(temp, workDir, names) { done, total ->
                if (total > 0) onProgress(done.toFloat() / total * 0.8f)
            }
            val root = DocumentFile.fromTreeUri(context, destinationTree)
                ?: throw IOException("Cannot open destination tree")
            copyDirectory(workDir, root)
            onProgress(1f)
            return count
        } finally {
            temp.delete()
            workDir.deleteRecursively()
        }
    }

    private fun copyDirectory(source: File, parent: DocumentFile) {
        source.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val dir = parent.findFile(file.name)
                    ?: parent.createDirectory(file.name)
                if (dir != null) copyDirectory(file, dir)
            } else {
                val existing = parent.findFile(file.name)
                val doc = existing?.takeIf { it.isFile }
                    ?: parent.createFile("application/octet-stream", file.name)
                    ?: return@forEach
                context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
            }
        }
    }
}
