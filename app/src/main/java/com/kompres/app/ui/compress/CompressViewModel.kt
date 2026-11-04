package com.kompres.app.ui.compress

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.PdfCompressManager
import com.kompres.app.data.SafHelper
import com.kompres.app.ui.common.UiMessage
import com.kompres.app.ui.common.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CompressMode(@StringRes val labelRes: Int, @StringRes val descRes: Int) {
    SAFE(R.string.compress_mode_safe, R.string.compress_mode_safe_desc),
    STRONG(R.string.compress_mode_strong, R.string.compress_mode_strong_desc)
}

data class CompressItem(val uri: Uri, val name: String)

data class CompressUiState(
    val input: CompressItem? = null,
    val mode: CompressMode = CompressMode.SAFE,
    val working: Boolean = false,
    val progress: Float = 0f,
    val message: UiMessage? = null
) {
    val outputName: String
        get() {
            val name = input?.name ?: return "compressed.pdf"
            val base = name.substringBeforeLast('.', name).ifBlank { "document" }
            return "${base}_compressed.pdf"
        }
}

class CompressViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = PdfCompressManager(application)
    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setInput(uri: Uri) {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                input = CompressItem(uri, SafHelper.displayName(context, uri)),
                message = null
            )
        }
    }

    fun setMode(mode: CompressMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun clear() {
        _state.update { it.copy(input = null, message = null) }
    }

    fun compress(output: Uri) {
        val current = _state.value.input ?: return
        val mode = _state.value.mode
        runJob {
            when (mode) {
                CompressMode.SAFE -> manager.safeCompress(current.uri, output) { p ->
                    _state.update { it.copy(progress = p) }
                }
                CompressMode.STRONG -> manager.strongCompress(current.uri, output) { p ->
                    _state.update { it.copy(progress = p) }
                }
            }
            val context = getApplication<Application>()
            val before = SafHelper.size(context, current.uri)
            val after = SafHelper.size(context, output)
            Analytics.trackEvent("pdf_compress", mapOf("mode" to mode.name.lowercase()))
            _state.update {
                it.copy(
                    working = false,
                    progress = 1f,
                    message = if (before != null && after != null) {
                        UiMessage(
                            R.string.msg_pdf_compressed,
                            listOf(formatBytes(before), formatBytes(after))
                        )
                    } else {
                        UiMessage(R.string.msg_pdf_compressed_done)
                    }
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(working = false, message = UiMessage(R.string.msg_cancelled)) }
    }

    private fun runJob(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, progress = 0f, message = null) }
            try {
                block()
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(working = false, message = UiMessage(R.string.msg_cancelled))
                }
                throw e
            } catch (e: Exception) {
                Analytics.trackEvent(
                    "conversion_failed",
                    mapOf("reason" to e.javaClass.simpleName)
                )
                _state.update {
                    it.copy(working = false, message = UiMessage(R.string.msg_error))
                }
            }
        }
    }
}
