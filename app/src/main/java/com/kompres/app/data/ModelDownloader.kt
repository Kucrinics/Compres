package com.kompres.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object OcrModels {
    const val BASE_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/4.1.0/"
    const val COMBINED = "rus+eng"

    val languages = listOf("rus", "eng")

    val hashes = mapOf(
        "eng" to "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
        "rus" to "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225"
    )

    val sizes = mapOf(
        "eng" to 4113088L,
        "rus" to 3861738L
    )
}

class ModelDownloader(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "tesseract/tessdata")

    fun isReady(): Boolean = OcrModels.languages.all { language ->
        val file = File(directory, "$language.traineddata")
        file.exists() && sha256(file) == OcrModels.hashes.getValue(language)
    }

    suspend fun ensureModels(onProgress: (Int) -> Unit = {}) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val languages = OcrModels.languages
        languages.forEachIndexed { index, language ->
            val target = File(directory, "$language.traineddata")
            val expectedHash = OcrModels.hashes.getValue(language)
            if (target.exists() && sha256(target) == expectedHash) {
                onProgress(((index + 1) * 100) / languages.size)
                return@forEachIndexed
            }
            download(language, target) { percent ->
                val overall = ((index + percent / 100f) / languages.size * 100f).toInt()
                onProgress(overall.coerceIn(0, 100))
            }
        }
    }

    private suspend fun download(
        language: String,
        target: File,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val url = URL(OcrModels.BASE_URL + "$language.traineddata")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
                .takeIf { it > 0 }
                ?: OcrModels.sizes.getValue(language)
            val temp = File(directory, "$language.tmp")
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            onProgress((done * 100 / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            val actual = digest.digest().toHex()
            if (actual != OcrModels.hashes.getValue(language)) {
                temp.delete()
                throw IOException("Checksum mismatch for $language")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        internal fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toHex()
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
