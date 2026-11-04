package com.kompres.app.ui.zip

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.ZipCore
import com.kompres.app.data.ZipManager
import com.kompres.app.ui.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ZipUiState(
    val working: Boolean = false,
    val progress: Float = 0f,
    val message: UiMessage? = null,
    val entries: List<ZipCore.Entry> = emptyList(),
    val previewZipUri: Uri? = null
)

class ZipViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ZipManager(application)
    private val _state = MutableStateFlow(ZipUiState())
    val state: StateFlow<ZipUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun create(inputs: List<Uri>, output: Uri, level: Int) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, progress = 0f, message = null) }
            try {
                manager.createArchive(inputs, output, level) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                Analytics.trackEvent("zip_create", mapOf("files" to inputs.size.toString()))
                _state.update {
                    it.copy(working = false, progress = 1f, message = UiMessage(R.string.msg_zip_created))
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(working = false, message = UiMessage(R.string.msg_cancelled)) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, message = UiMessage(R.string.msg_error)) }
            }
        }
    }

    fun preview(zipUri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, progress = 0f, message = null) }
            try {
                val entries = manager.listEntries(zipUri)
                Analytics.trackEvent("zip_preview", mapOf("entries" to entries.size.toString()))
                _state.update {
                    it.copy(
                        working = false,
                        entries = entries,
                        previewZipUri = zipUri,
                        message = UiMessage(R.string.msg_zip_entries, listOf(entries.size))
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(working = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, message = UiMessage(R.string.msg_error)) }
            }
        }
    }

    fun extractAll(zipUri: Uri, destination: Uri) {
        extractInternal(zipUri, destination, null)
    }

    fun extractSelected(zipUri: Uri, destination: Uri, names: Set<String>) {
        extractInternal(zipUri, destination, names)
    }

    private fun extractInternal(zipUri: Uri, destination: Uri, names: Set<String>?) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, progress = 0f, message = null) }
            try {
                val count = if (names == null) {
                    manager.extractAll(zipUri, destination) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                } else {
                    manager.extractSelected(zipUri, destination, names) { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                }
                Analytics.trackEvent("zip_extract", mapOf("files" to count.toString()))
                _state.update {
                    it.copy(
                        working = false,
                        progress = 1f,
                        message = UiMessage(R.string.msg_zip_extracted, listOf(count))
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(working = false, message = UiMessage(R.string.msg_cancelled)) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(working = false, message = UiMessage(R.string.msg_error)) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(working = false, message = UiMessage(R.string.msg_cancelled)) }
    }

    fun clearPreview() {
        _state.update { it.copy(entries = emptyList(), previewZipUri = null, message = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
