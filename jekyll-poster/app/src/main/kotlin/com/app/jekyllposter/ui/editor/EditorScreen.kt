package com.app.jekyllposter.ui.editor

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.produceState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.ui.editor.EditorViewModel.TermKind
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Public
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.ui.home.status

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val text = viewModel.text
    var picker by remember { mutableStateOf<TermKind?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    BackHandler(onBack = viewModel::close)
    val editable = state.editable
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Asked on the first Publish, when "tell you when it's live" makes sense; publishing goes ahead either way.
    val askToNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val askForNotifications = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Unit
    }
    val jekyllDraft = text?.editingPath?.contains("_drafts/") == true
    val send = { destination: Destination ->
        // Only a post for the site gets a "live" notification.
        if (destination == Destination.Posts) askForNotifications()
        viewModel.publish(destination)
    }
    LaunchedEffect(state.photoError) {
        state.photoError?.let { snackbar.showSnackbar(it); viewModel.dismissPhotoError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.draft?.state == PostState.Published -> "Published"
                            state.draft?.state == PostState.Queued -> "Publishing"
                            text?.editingPath != null -> "Edit post"
                            else -> "New post"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = viewModel::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (editable && !state.previewing) {
                        if (state.addingPhoto) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Icon(Icons.Default.AddPhotoAlternate, "Add a photo")
                            }
                        }
                    }
                    IconButton(onClick = viewModel::togglePreview) {
                        if (state.previewing) Icon(Icons.Default.EditNote, "Back to writing")
                        else Icon(Icons.Default.Visibility, "Preview")
                    }
                    if (editable) {
                        TextButton(onClick = { send(if (jekyllDraft) Destination.Drafts else Destination.Posts) }) {
                            Text(
                                when {
                                    jekyllDraft -> "Update draft"
                                    text?.editingPath != null -> "Update"
                                    else -> "Publish"
                                },
                            )
                        }
                    }
                    // A queued post may be mid-commit; deleting it then would lose the phone's record
                    // of a post that still goes out.
                    if (state.draft?.state != PostState.Queued) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (editable && text?.editingPath == null) {
                                DropdownMenuItem(
                                    text = { Text("Save to the blog's _drafts") },
                                    leadingIcon = { Icon(Icons.Default.Drafts, null) },
                                    onClick = { menu = false; send(Destination.Drafts) },
                                )
                            }
                            if (editable && jekyllDraft) {
                                DropdownMenuItem(
                                    text = { Text("Publish to the site") },
                                    leadingIcon = { Icon(Icons.Default.Public, null) },
                                    onClick = { menu = false; send(Destination.Posts) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(deleteLabel(state.draft)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (text == null) return@Scaffold
        if (state.previewing) {
            PostPreview(viewModel, Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
            state.draft?.takeIf { !editable || it.error != null }?.let { draft ->
                val (label, isError) = draft.status()
                val uri = LocalUriHandler.current
                Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        if (draft.state == PostState.Published && draft.postUrl != null) {
                            TextButton(onClick = { uri.openUri(draft.postUrl) }) { Text("Open on the site") }
                        }
                    }
                }
            }
            TextField(
                value = text.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineSmall) },
                textStyle = MaterialTheme.typography.headlineSmall,
                readOnly = !editable,
                isError = state.titleMissing,
                supportingText = if (state.titleMissing) ({ Text("A post needs a title: it becomes the address.") }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                colors = plainField(),
                modifier = Modifier.fillMaxWidth().testTag("title"),
            )
            TermRow("Categories", text.categories, editable, onAdd = { picker = TermKind.Category }, onRemove = { viewModel.remove(TermKind.Category, it) })
            TermRow("Tags", text.tags, editable, onAdd = { picker = TermKind.Tag }, onRemove = { viewModel.remove(TermKind.Tag, it) })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            TextField(
                value = text.body,
                onValueChange = viewModel::setBody,
                placeholder = { Text("Write in Markdown…") },
                readOnly = !editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = plainField(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).testTag("body"),
            )
        }
    }

    picker?.let { kind ->
        TermPicker(kind, viewModel, onDismiss = { picker = null })
    }
    if (confirmDelete) {
        val published = state.draft?.state == PostState.Published
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(if (published) "Remove from this list?" else if (text?.editingPath != null) "Discard your changes?" else "Delete this draft?") },
            text = {
                Text(
                    when {
                        published -> "The post stays on your blog; this only clears it from the phone."
                        text?.editingPath != null -> "The post on your blog stays as it is."
                        else -> "It's only on this phone, so it can't be brought back."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(if (published) "Remove" else if (text?.editingPath != null) "Discard" else "Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun PostPreview(viewModel: EditorViewModel, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val html by produceState("", viewModel.text, dark) { value = viewModel.previewHtml(dark) }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // The writer's own HTML, but still: no scripts, no file access.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // A tapped link opens in the browser, not in place of the preview.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                }
            }
        },
        update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) },
        modifier = modifier.semantics { contentDescription = "Preview of the post" },
    )
}

private fun deleteLabel(draft: com.app.jekyllposter.data.Draft?) = when {
    draft?.state == PostState.Published -> "Remove from this list"
    draft?.editingPath != null -> "Discard changes"
    else -> "Delete draft"
}

@Composable
private fun plainField() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TermRow(label: String, terms: List<String>, editable: Boolean, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    if (!editable && terms.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            terms.forEach { term ->
                InputChip(
                    selected = false,
                    enabled = editable,
                    onClick = { onRemove(term) },
                    label = { Text(term) },
                    trailingIcon = if (editable) ({ Icon(Icons.Default.Close, "Remove $term") }) else null,
                )
            }
            if (editable) {
                AssistChip(
                    onClick = onAdd,
                    label = { Text(if (terms.isEmpty()) "Add" else "More") },
                    leadingIcon = { Icon(Icons.Default.Add, null) },
                    modifier = Modifier.semantics { contentDescription = "Add ${label.lowercase().removeSuffix("s").replace("categorie", "category")}" },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermPicker(kind: TermKind, viewModel: EditorViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val noun = if (kind == TermKind.Category) "category" else "tag"
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Recomputed on each keystroke and each change to the post's picks.
    val state by viewModel.state.collectAsStateWithLifecycle()
    val suggestions = remember(query, state) { viewModel.suggestions(kind, query) }
    val exact = suggestions.any { it.name.equals(query.trim(), ignoreCase = true) }
    val add = { name: String -> viewModel.add(kind, name); query = "" }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(if (kind == TermKind.Category) "Find or add a category" else "Find or add a tag") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val match = suggestions.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
                    add(match?.name ?: query)
                }),
                modifier = Modifier.fillMaxWidth().testTag("termQuery"),
            )
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            if (query.isNotBlank() && !exact) {
                item {
                    Row(Modifier.fillMaxWidth().clickable { add(query) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, null, Modifier.padding(end = 12.dp))
                        Text("Add “${query.trim().trimStart('#')}” as a new $noun")
                    }
                }
            }
            if (suggestions.isEmpty() && query.isBlank()) {
                item {
                    Text(
                        "Your blog has no ${noun}s yet that this post doesn't already have. Type one to add it.",
                        Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(suggestions, key = { it.name }) { term ->
                Row(Modifier.fillMaxWidth().clickable { add(term.name) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(term.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(if (term.count == 1) "1 post" else "${term.count} posts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Done") }
    }
}
