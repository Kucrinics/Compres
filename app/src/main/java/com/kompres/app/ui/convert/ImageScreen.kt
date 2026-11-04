package com.kompres.app.ui.convert

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kompres.app.R
import com.kompres.app.data.ImageFormat
import com.kompres.app.ui.common.CreateDocumentWithMime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageScreen(onBack: () -> Unit) {
    val vm: ImageViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    var showCustomRatio by remember { mutableStateOf(false) }

    val openImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.setItems(uris)
    }

    val createDocument = rememberLauncherForActivityResult(
        CreateDocumentWithMime { state.format.mime }
    ) { uri ->
        if (uri != null) vm.convertToUri(uri)
    }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        if (tree != null) vm.convertToTree(tree)
    }

    if (showCustomRatio) {
        CustomRatioDialog(
            onDismiss = { showCustomRatio = false },
            onConfirm = { ratio ->
                vm.setCustomRatio(ratio)
                showCustomRatio = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.card_images)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Button(
                onClick = { openImages.launch(arrayOf("image/*")) },
                enabled = !state.working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.img_select))
            }

            if (state.items.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.img_selected_count, state.items.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    state.items.take(5).forEach { item ->
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.items.size > 5) {
                        Text(
                            text = "+${state.items.size - 5}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.img_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.format == format,
                        onClick = { vm.setFormat(format) },
                        label = { Text(format.displayName) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.img_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = state.quality.toString(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Slider(
                value = state.quality.toFloat(),
                onValueChange = { vm.setQuality(it.toInt()) },
                valueRange = 0f..100f,
                enabled = state.format.lossy
            )

            SectionTitle(stringResource(R.string.img_scale))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(100, 75, 50, 25).forEach { percent ->
                    FilterChip(
                        selected = state.scalePercent == percent,
                        onClick = { vm.setScale(percent) },
                        label = { Text(stringResource(R.string.img_scale_percent, percent)) }
                    )
                }
            }

            SectionTitle(stringResource(R.string.img_crop))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CropAspect.entries.forEach { aspect ->
                    FilterChip(
                        selected = state.crop == aspect,
                        onClick = {
                            if (aspect == CropAspect.CUSTOM) {
                                showCustomRatio = true
                            } else {
                                vm.setCrop(aspect)
                            }
                        },
                        label = { Text(stringResource(aspect.labelRes)) }
                    )
                }
            }

            if (state.working) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = vm::cancel) {
                        Text(stringResource(R.string.zip_cancel))
                    }
                }
            }

            state.message?.let { message ->
                Text(
                    text = if (message.args.isEmpty()) {
                        stringResource(message.resId)
                    } else {
                        stringResource(message.resId, *message.args.toTypedArray())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Button(
                onClick = {
                    val first = state.items.firstOrNull() ?: return@Button
                    if (state.items.size == 1) {
                        val base = first.name.substringBeforeLast('.', first.name).ifBlank { "image" }
                        createDocument.launch("${base}_converted.${state.format.extension}")
                    } else {
                        openTree.launch(null)
                    }
                },
                enabled = state.items.isNotEmpty() && !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.img_convert))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun CustomRatioDialog(
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var width by remember { mutableStateOf("4") }
    var height by remember { mutableStateOf("3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.img_custom_ratio_title)) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text(stringResource(R.string.img_custom_w)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(text = " : ", modifier = Modifier.padding(horizontal = 8.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text(stringResource(R.string.img_custom_h)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = width.toFloatOrNull()
                    val h = height.toFloatOrNull()
                    if (w != null && h != null && w > 0f && h > 0f) {
                        onConfirm(w / h)
                    }
                }
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
