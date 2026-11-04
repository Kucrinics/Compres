package com.kompres.app.data

import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipCore {

    data class Source(val name: String, val open: () -> InputStream)

    data class Entry(
        val name: String,
        val size: Long,
        val compressedSize: Long,
        val isDirectory: Boolean
    )

    fun list(file: File): List<Entry> =
        ZipFile(file).use { zip ->
            zip.entries().asSequence().map {
                Entry(it.name, it.size, it.compressedSize, it.isDirectory)
            }.toList()
        }

    fun create(
        sources: List<Source>,
        output: OutputStream,
        level: Int,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val used = mutableSetOf<String>()
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.setLevel(level)
            sources.forEachIndexed { index, source ->
                val name = uniqueName(source.name, used)
                zip.putNextEntry(ZipEntry(name))
                source.open().use { input -> input.copyTo(zip) }
                zip.closeEntry()
                onProgress(index + 1, sources.size)
            }
        }
    }

    fun extract(
        zip: File,
        destinationRoot: File,
        selected: Set<String>? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        var count = 0
        var processed = 0
        ZipFile(zip).use { archive ->
            val entries = archive.entries().toList()
            val total = entries.size
            for (entry in entries) {
                processed++
                if (!entry.isDirectory && (selected == null || entry.name in selected)) {
                    val safe = sanitize(entry.name)
                    if (safe != null) {
                        val out = File(destinationRoot, safe)
                        val parent = out.parentFile
                        if (parent != null) {
                            parent.mkdirs()
                            val rootPath = destinationRoot.canonicalPath
                            if (parent.canonicalPath.startsWith(rootPath)) {
                                archive.getInputStream(entry).use { input ->
                                    out.outputStream().use { output -> input.copyTo(output) }
                                }
                                count++
                            }
                        }
                    }
                }
                onProgress(processed, total)
            }
        }
        return count
    }

    fun sanitize(name: String): String? {
        val raw = name.replace('\\', '/')
        if (raw.startsWith("/")) return null
        val parts = raw.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    fun uniqueName(name: String, used: MutableSet<String>): String {
        val candidate = if (name.isBlank()) "file" else name
        if (used.add(candidate)) return candidate
        val dot = candidate.lastIndexOf('.')
        val base = if (dot > 0) candidate.substring(0, dot) else candidate
        val ext = if (dot > 0) candidate.substring(dot) else ""
        var counter = 1
        while (true) {
            val next = "$base ($counter)$ext"
            if (used.add(next)) return next
            counter++
        }
    }
}
