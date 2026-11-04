package com.kompres.app.ui.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kompres.app.R
import com.kompres.app.data.SafHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

@Composable
fun PdfPreview(uri: Uri) {
    val context = LocalContext.current
    val state = remember(uri) { mutableStateOf<PdfPreviewState>(PdfPreviewState.Loading) }
    LaunchedEffect(uri) {
        state.value = withContext(Dispatchers.IO) {
            val opened = runCatching { PdfSource.open(context, uri) }.getOrNull()
            if (opened == null) PdfPreviewState.Failed else PdfPreviewState.Ready(opened)
        }
    }
    val current = state.value
    DisposableEffect(current) {
        onDispose { (current as? PdfPreviewState.Ready)?.source?.close() }
    }

    when (current) {
        PdfPreviewState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        PdfPreviewState.Failed -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.msg_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is PdfPreviewState.Ready -> {
            if (current.source.pageCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.msg_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val pageWidthPx = with(density) { maxWidth.roundToPx() }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(current.source.pageCount) { index ->
                            PdfPage(current.source, index, pageWidthPx)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(source: PdfSource, index: Int, widthPx: Int) {
    var page by remember(source, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source, index, widthPx) {
        page = withContext(Dispatchers.IO) { source.render(index, widthPx) }
    }
    val currentPage = page
    DisposableEffect(currentPage) {
        onDispose { currentPage?.recycle() }
    }
    if (currentPage == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(A4_ASPECT_RATIO)
                .background(ComposeColor.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Image(
            bitmap = currentPage.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .background(ComposeColor.White)
        )
    }
}

private sealed interface PdfPreviewState {
    data object Loading : PdfPreviewState
    data object Failed : PdfPreviewState
    data class Ready(val source: PdfSource) : PdfPreviewState
}

private class PdfSource private constructor(
    private val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int
) {
    private val mutex = Mutex()

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (index !in 0 until pageCount || widthPx <= 0) return@withContext null
            val page = renderer.openPage(index)
            try {
                val scale = minOf(
                    widthPx.toFloat() / page.width,
                    MAX_RENDER_SIDE.toFloat() / page.height
                )
                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Cannot render PDF page $index", e)
                null
            } finally {
                page.close()
            }
        }
    }

    fun close() {
        runBlocking {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
                file.delete()
            }
        }
    }

    companion object {
        private const val MAX_RENDER_SIDE = 4096

        fun open(context: Context, uri: Uri): PdfSource? {
            val file = try {
                SafHelper.copyToCache(context, uri)
            } catch (e: IOException) {
                Log.e(TAG, "Cannot copy PDF to cache", e)
                return null
            }
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open PDF descriptor", e)
                file.delete()
                return null
            }
            val renderer = try {
                PdfRenderer(descriptor)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open PDF", e)
                runCatching { descriptor.close() }
                file.delete()
                return null
            }
            return PdfSource(file, descriptor, renderer, renderer.pageCount)
        }

        private const val TAG = "PdfPreview"
    }
}

private const val A4_ASPECT_RATIO = 210f / 297f
