package com.kompres.app.ui.docs

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.OfficeTextExtractor
import com.kompres.app.data.SafHelper
import com.kompres.app.ui.common.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DocKind(
    val extension: String,
    val mime: String,
    val canEdit: Boolean,
    val previewable: Boolean
) {
    PDF("pdf", "application/pdf", false, true),
    DOCX(
        "docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        true,
        true
    ),
    XLSX(
        "xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        true,
        true
    ),
    PPTX(
        "pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        true,
        true
    ),
    DOC("doc", "application/msword", true, false),
    TEXT("txt", "text/plain", true, true),
    UNKNOWN("", "application/octet-stream", false, false);

    companion object {
        fun fromName(name: String): DocKind {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "pdf" -> PDF
                "docx" -> DOCX
                "xlsx" -> XLSX
                "pptx" -> PPTX
                "doc" -> DOC
                "txt", "md", "csv", "log", "json", "xml" -> TEXT
                else -> UNKNOWN
            }
        }
    }
}

data class DocsUiState(
    val uri: Uri? = null,
    val name: String = "",
    val kind: DocKind = DocKind.UNKNOWN,
    val text: String? = null,
    val loading: Boolean = false,
    val message: UiMessage? = null
)

class DocsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application
    private val _state = MutableStateFlow(DocsUiState())
    val state: StateFlow<DocsUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun open(uri: Uri) {
        job?.cancel()
        val name = SafHelper.displayName(context, uri)
        val kind = DocKind.fromName(name)
        _state.value = DocsUiState(uri = uri, name = name, kind = kind)
        Analytics.trackEvent("doc_open", mapOf("ext" to kind.extension))

        when {
            !kind.previewable -> {
                _state.update { it.copy(message = UiMessage(R.string.msg_docs_no_preview)) }
            }
            kind != DocKind.PDF && kind != DocKind.UNKNOWN -> {
                job = viewModelScope.launch {
                    _state.update { it.copy(loading = true) }
                    try {
                        val text = withContext(Dispatchers.IO) {
                            val file = SafHelper.copyToCache(context, uri)
                            try {
                                OfficeTextExtractor.extract(file, kind.extension)
                            } finally {
                                file.delete()
                            }
                        }
                        _state.update {
                            it.copy(
                                loading = false,
                                text = text,
                                message = if (text.isNullOrBlank()) {
                                    UiMessage(R.string.msg_docs_no_preview)
                                } else {
                                    null
                                }
                            )
                        }
                    } catch (_: Exception) {
                        _state.update {
                            it.copy(
                                loading = false,
                                message = UiMessage(R.string.msg_error)
                            )
                        }
                    }
                }
            }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = DocsUiState()
    }

    fun showError() {
        _state.update { it.copy(message = UiMessage(R.string.msg_error)) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }
}
