package com.kompres.app.ui.convert

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompres.app.R
import com.kompres.app.analytics.Analytics
import com.kompres.app.data.ImageFormat
import com.kompres.app.data.ImageManager
import com.kompres.app.data.ImageOptions
import com.kompres.app.data.SafHelper
import com.kompres.app.ui.common.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CropAspect(val ratio: Float?, @StringRes val labelRes: Int) {
    ORIGINAL(null, R.string.img_crop_original),
    R1_1(1f, R.string.img_crop_1_1),
    R4_3(4f / 3f, R.string.img_crop_4_3),
    R16_9(16f / 9f, R.string.img_crop_16_9),
    R18_9(18f / 9f, R.string.img_crop_18_9),
    R19_6(19f / 6f, R.string.img_crop_19_6),
    R21_9(21f / 9f, R.string.img_crop_21_9),
    CUSTOM(null, R.string.img_crop_custom)
}

data class ImageItem(val uri: Uri, val name: String)

data class ImageUiState(
    val items: List<ImageItem> = emptyList(),
    val format: ImageFormat = ImageFormat.JPG,
    val quality: Int = 85,
    val scalePercent: Int = 100,
    val crop: CropAspect = CropAspect.ORIGINAL,
    val customRatio: Float = 4f / 3f,
    val working: Boolean = false,
    val progress: Float = 0f,
    val message: UiMessage? = null
) {
    val effectiveCropRatio: Float?
        get() = if (crop == CropAspect.CUSTOM) customRatio else crop.ratio

    fun options(): ImageOptions = ImageOptions(
        format = format,
        quality = quality,
        scalePercent = scalePercent,
        cropRatio = effectiveCropRatio
    )
}

class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = ImageManager(application)
    private val _state = MutableStateFlow(ImageUiState())
    val state: StateFlow<ImageUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun setItems(uris: List<Uri>) {
        val context = getApplication<Application>()
        val items = uris.map { ImageItem(it, SafHelper.displayName(context, it)) }
        _state.update { it.copy(items = items, message = null) }
    }

    fun setFormat(format: ImageFormat) {
        _state.update { it.copy(format = format) }
    }

    fun setQuality(quality: Int) {
        _state.update { it.copy(quality = quality.coerceIn(0, 100)) }
    }

    fun setScale(percent: Int) {
        _state.update { it.copy(scalePercent = percent) }
    }

    fun setCrop(aspect: CropAspect) {
        _state.update { it.copy(crop = aspect) }
    }

    fun setCustomRatio(ratio: Float) {
        if (ratio <= 0f) return
        _state.update { it.copy(customRatio = ratio, crop = CropAspect.CUSTOM) }
    }

    fun clear() {
        _state.update { it.copy(items = emptyList(), message = null) }
    }

    fun convertToUri(output: Uri) {
        val input = _state.value.items.firstOrNull() ?: return
        val options = _state.value.options()
        runJob {
            val result = manager.convertToUri(input.uri, output, options)
            Analytics.trackEvent(
                "image_convert",
                mapOf("format" to options.format.name, "count" to "1")
            )
            _state.update {
                it.copy(
                    working = false,
                    progress = 1f,
                    message = UiMessage(R.string.msg_image_done)
                )
            }
            result
        }
    }

    fun convertToTree(tree: Uri) {
        val items = _state.value.items
        if (items.isEmpty()) return
        val options = _state.value.options()
        runJob {
            val (success, failed) = manager.convertToTree(items.map { it.uri }, tree, options) { p ->
                _state.update { it.copy(progress = p) }
            }
            Analytics.trackEvent(
                "image_convert",
                mapOf("format" to options.format.name, "count" to success.toString())
            )
            if (failed > 0) {
                Analytics.trackEvent("conversion_failed", mapOf("count" to failed.toString()))
            }
            _state.update {
                it.copy(
                    working = false,
                    progress = 1f,
                    message = if (failed > 0) {
                        UiMessage(R.string.msg_images_done_with_failures, listOf(success, failed))
                    } else {
                        UiMessage(R.string.msg_images_done, listOf(success))
                    }
                )
            }
            success
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(working = false, message = UiMessage(R.string.msg_cancelled)) }
    }

    private fun runJob(block: suspend () -> Any?) {
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
                Analytics.trackEvent("conversion_failed", mapOf("reason" to (e.javaClass.simpleName)))
                _state.update {
                    it.copy(working = false, message = UiMessage(R.string.msg_error))
                }
            }
        }
    }
}
