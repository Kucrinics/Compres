package com.kompres.app.ui.ocr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.BitmapLoader
import com.kompres.app.data.ModelDownloader
import com.kompres.app.data.OcrEngine
import com.kompres.app.ui.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class OcrUiState(
    val imageUri: Uri? = null,
    val bitmap: Bitmap? = null,
    val text: String = "",
    val working: Boolean = false,
    val modelProgress: Int = -1,
    val message: UiMessage? = null
)

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = ModelDownloader(application)
    private val engine = OcrEngine(application)
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setImage(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update {
                it.copy(imageUri = uri, text = "", message = null, modelProgress = -1)
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapLoader.load(getApplication(), uri)
                }
                _state.update {
                    it.copy(
                        bitmap = bitmap,
                        message = if (bitmap == null) UiMessage(R.string.msg_error) else null
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(message = UiMessage(R.string.msg_error)) }
            }
        }
    }

    fun recognize() {
        if (_state.value.imageUri == null) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(working = true, message = null) }
            try {
                if (!downloader.isReady()) {
                    _state.update { it.copy(modelProgress = 0) }
                    downloader.ensureModels { progress ->
                        _state.update { it.copy(modelProgress = progress) }
                    }
                    Analytics.trackEvent("ocr_model_download")
                }
                _state.update { it.copy(modelProgress = -1) }

                val bitmap = _state.value.bitmap ?: withContext(Dispatchers.IO) {
                    BitmapLoader.load(getApplication(), _state.value.imageUri!!)
                } ?: throw IOException("Cannot decode image")

                val text = engine.recognize(bitmap)
                Analytics.trackEvent("ocr_scan")
                _state.update {
                    it.copy(
                        working = false,
                        text = text,
                        message = if (text.isBlank()) {
                            UiMessage(R.string.ocr_no_text)
                        } else {
                            UiMessage(R.string.ocr_done)
                        }
                    )
                }
            } catch (e: CancellationException) {
                _state.update {
                    it.copy(
                        working = false,
                        modelProgress = -1,
                        message = UiMessage(R.string.msg_cancelled)
                    )
                }
                throw e
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        working = false,
                        modelProgress = -1,
                        message = UiMessage(R.string.msg_error)
                    )
                }
            }
        }
    }

    fun updateText(text: String) {
        _state.update { it.copy(text = text) }
    }

    fun save(output: Uri) {
        val text = _state.value.text
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(output)
                        ?: throw IOException("Cannot open output")
                    stream.use { it.write(text.toByteArray()) }
                }
                _state.update { it.copy(message = UiMessage(R.string.ocr_saved)) }
            } catch (_: Exception) {
                _state.update { it.copy(message = UiMessage(R.string.msg_error)) }
            }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = OcrUiState()
    }

    fun cancel() {
        job?.cancel()
        _state.update {
            it.copy(
                working = false,
                modelProgress = -1,
                message = UiMessage(R.string.msg_cancelled)
            )
        }
    }
}
