package com.kompres.app.ui.zip

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.kompres.app.ui.common.formatBytes

private enum class PendingPick { EXTRACT, PREVIEW }

private val zipMimeTypes = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipScreen(onBack: () -> Unit) {
    val vm: ZipViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    var level by remember { mutableIntStateOf(6) }
    var pendingInputs by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingPick by remember { mutableStateOf<PendingPick?>(null) }
    var pendingZip by remember { mutableStateOf<Uri?>(null) }
    var pendingSelected by remember { mutableStateOf<Set<String>?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(state.entries) { selected = emptySet() }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { output ->
        val inputs = pendingInputs
        if (output != null && inputs != null) vm.create(inputs, output, level)
        pendingInputs = null
    }

    val openMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingInputs = uris
            createDocument.launch("archive.zip")
        }
    }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val zip = pendingZip
        if (tree != null && zip != null) {
            val chosen = pendingSelected
            if (chosen != null) vm.extractSelected(zip, tree, chosen) else vm.extractAll(zip, tree)
        }
        pendingZip = null
        pendingSelected = null
    }

    val openZip = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            when (pendingPick) {
                PendingPick.PREVIEW -> vm.preview(uri)
                PendingPick.EXTRACT -> {
                    pendingZip = uri
                    openTree.launch(null)
                }
                null -> Unit
            }
        }
        pendingPick = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.card_zip)) },
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
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.zip_level),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                LevelChip(R.string.zip_level_store, 0, level) { level = it }
                LevelChip(R.string.zip_level_fast, 1, level) { level = it }
                LevelChip(R.string.zip_level_normal, 6, level) { level = it }
                LevelChip(R.string.zip_level_max, 9, level) { level = it }
            }

            Button(
                onClick = { openMultiple.launch(arrayOf("*/*")) },
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.zip_create))
            }

            OutlinedButton(
                onClick = {
                    pendingPick = PendingPick.EXTRACT
                    openZip.launch(zipMimeTypes)
                },
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.zip_extract))
            }

            OutlinedButton(
                onClick = {
                    pendingPick = PendingPick.PREVIEW
                    openZip.launch(zipMimeTypes)
                },
                enabled = !state.working,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.zip_preview))
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

            if (state.entries.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.msg_zip_entries, state.entries.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = vm::clearPreview) {
                        Text(stringResource(R.string.zip_clear))
                    }
                }
                OutlinedButton(
                    onClick = {
                        val zip = state.previewZipUri
                        if (zip != null && selected.isNotEmpty()) {
                            pendingSelected = selected
                            pendingZip = zip
                            openTree.launch(null)
                        }
                    },
                    enabled = !state.working && selected.isNotEmpty(),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(stringResource(R.string.zip_extract_selected, selected.size))
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.entries, key = { it.name }) { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (entry.name in selected) {
                                        selected - entry.name
                                    } else {
                                        selected + entry.name
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = entry.name in selected,
                                onCheckedChange = {
                                    selected = if (entry.name in selected) {
                                        selected - entry.name
                                    } else {
                                        selected + entry.name
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!entry.isDirectory) {
                                    Text(
                                        text = formatBytes(entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelChip(labelRes: Int, value: Int, selected: Int, onSelect: (Int) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) }
    )
}
