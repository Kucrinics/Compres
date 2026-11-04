package com.kompres.app.ui.merge

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.PdfMergeManager
import com.kompres.app.data.SafHelper
import com.kompres.app.ui.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MergeMode(@StringRes val labelRes: Int) {
    PDFS(R.string.merge_mode_pdfs),
    IMAGES(R.string.merge_mode_images)
}

data class MergeItem(val uri: Uri, val name: String)

data class MergeUiState(
    val mode: MergeMode = MergeMode.PDFS,
    val items: List<MergeItem> = emptyList(),
    val working: Boolean = false,
    val progress: Float = 0f,
    val message: UiMessage? = null
) {
    val outputName: String
        get() = when (mode) {
            MergeMode.PDFS -> "merged.pdf"
            MergeMode.IMAGES -> "images.pdf"
        }
}

class MergeViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = PdfMergeManager(application)
    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setMode(mode: MergeMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode, items = emptyList(), message = null) }
    }

    fun addItems(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val context = getApplication<Application>()
        val additions = uris.map { MergeItem(it, SafHelper.displayName(context, it)) }
        _state.update { it.copy(items = it.items + additions, message = null) }
    }

    fun removeAt(index: Int) {
        _state.update { state ->
            if (index !in state.items.indices) return@update state
            val updated = state.items.toMutableList().apply { removeAt(index) }
            state.copy(items = updated)
        }
    }

    fun moveUp(index: Int) {
        _state.update { state ->
            if (index <= 0 || index >= state.items.size) return@update state
            val updated = state.items.toMutableList()
            val item = updated.removeAt(index)
            updated.add(index - 1, item)
            state.copy(items = updated)
        }
    }

    fun moveDown(index: Int) {
        _state.update { state ->
            if (index < 0 || index >= state.items.lastIndex) return@update state
            val updated = state.items.toMutableList()
            val item = updated.removeAt(index)
            updated.add(index + 1, item)
            state.copy(items = updated)
        }
    }

    fun clear() {
        _state.update { it.copy(items = emptyList(), message = null) }
    }

    fun create(output: Uri) {
        val current = _state.value
        if (current.items.isEmpty()) return
        val uris = current.items.map { it.uri }
        val mode = current.mode
        runJob {
            when (mode) {
                MergeMode.PDFS -> manager.mergePdfs(uris, output) { p ->
                    _state.update { it.copy(progress = p) }
                }
                MergeMode.IMAGES -> manager.imagesToPdf(uris, output) { p ->
                    _state.update { it.copy(progress = p) }
                }
            }
            Analytics.trackEvent(
                if (mode == MergeMode.PDFS) "pdf_merge" else "images_to_pdf",
                mapOf("count" to uris.size.toString())
            )
            _state.update {
                it.copy(
                    working = false,
                    progress = 1f,
                    message = UiMessage(R.string.msg_pdf_ready)
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
