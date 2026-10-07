package com.app.jekyllposter.ui.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.core.jekyll.Edit
import com.app.jekyllposter.core.jekyll.PostPath
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.ui.editor.EditorViewModel.TermKind
import com.app.jekyllposter.ui.home.nowUntil
import com.app.jekyllposter.ui.home.status
import com.app.jekyllposter.ui.theme.InkButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val text = viewModel.text
    var picker by remember { mutableStateOf<TermKind?>(null) }
    // The toolbar formats the body, so it only works while the body has the focus.
    var bodyFocused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteFromBlog by remember { mutableStateOf(false) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    BackHandler(onBack = viewModel::close)
    val editable = state.editable
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::addPhoto)
    }
    // Saved state, not the ViewModel: the camera app may push this app out of memory meanwhile.
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        cameraPath?.let { viewModel.photoTaken(it, taken) }
        cameraPath = null
    }
    val chooseVault = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) viewModel.vaultChosen(tree)
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
    val jekyllDraft = text?.editingPath?.let { PostPath(it).isDraft } == true
    val send = { destination: Destination ->
        // Only a post for the site gets a "live" notification.
        if (destination == Destination.Posts) askForNotifications()
        viewModel.publish(destination)
    }
    LaunchedEffect(state.photoError) {
        state.photoError?.let { snackbar.showSnackbar(it); viewModel.dismissPhotoError() }
    }
    LaunchedEffect(state.frontMatterBlocked) {
        state.frontMatterBlocked?.let { snackbar.showSnackbar("Not published: the front matter needs fixing. $it") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.draft?.state == PostState.Published -> "Published"
                            state.draft?.state == PostState.Queued && state.draft?.destination == Destination.Delete -> "Deleting"
                            state.draft?.state == PostState.Queued -> "Publishing"
                            text?.editingPath != null -> "Edit post"
                            else -> "New post"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = viewModel::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = viewModel::togglePreview) {
                        if (state.previewing) Icon(Icons.Default.EditNote, "Back to writing")
                        else Icon(Icons.Default.Visibility, "Preview")
                    }
                    if (editable) {
                        InkButton(
                            when {
                                jekyllDraft -> "Update draft"
                                text?.editingPath != null -> "Update"
                                else -> "Publish"
                            },
                        ) { send(if (jekyllDraft) Destination.Drafts else Destination.Posts) }
                    }
                    // Waiting for its random time: the writer can still send it at once.
                    if (state.draft?.state == PostState.Queued && (state.draft?.sendAfter ?: 0) > nowUntil(state.draft?.sendAfter)) {
                        InkButton("Send now", onClick = viewModel::sendNow)
                    }
                    // A queued post may be mid-commit; deleting it then would lose the phone's record
                    // of a post that still goes out.
                    if (state.draft?.state != PostState.Queued) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (editable && text?.editingPath == null) {
                                DropdownMenuItem(
                                    text = {
                                        // _drafts sounds private, but a public repository is readable by anyone.
                                        Column {
                                            Text("Save to the blog's _drafts")
                                            Text(
                                                "Not on the site, but readable on GitHub if the repository is public",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
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
                            if (editable && text?.editingPath != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete from the blog") },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, null) },
                                    onClick = { menu = false; confirmDeleteFromBlog = true },
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
        bottomBar = {
            if (editable && !state.previewing && text != null) Column {
                // Typing `[[` offers the blog's posts, as Obsidian offers notes.
                val linkable by viewModel.linkable.collectAsStateWithLifecycle()
                viewModel.openLink?.takeIf { bodyFocused }?.let { link ->
                    LinkSuggestions(viewModel.postsToLink(link.query, linkable), link.query, anyPosts = linkable.isNotEmpty(), onPick = viewModel::linkTo)
                }
                FormatBar(
                    formatting = bodyFocused,
                    addingPhoto = state.addingPhoto,
                    onFormat = viewModel::format,
                    onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onTakePhoto = take@{
                        // One at a time: a second tap before the camera opens would orphan a file.
                        if (cameraPath != null) return@take
                        val target = viewModel.cameraTarget() ?: return@take viewModel.cameraNotReady()
                        cameraPath = target.path
                        try {
                            takePhoto.launch(target.uri)
                        } catch (e: ActivityNotFoundException) {
                            cameraPath = null
                            viewModel.cameraUnavailable(target.path)
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (text == null) return@Scaffold
        if (state.previewing) {
            PostPreview(viewModel, Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            state.draft?.takeIf { !editable || it.error != null }?.let { draft ->
                val waitingForVpn by viewModel.waitingForVpn.collectAsStateWithLifecycle()
                val (label, isError) = draft.status(waitingForVpn = waitingForVpn, now = nowUntil(draft.sendAfter))
                val uri = LocalUriHandler.current
                Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        if (draft.state == PostState.Published && draft.postUrl != null) {
                            TextButton(onClick = { uri.openUri(draft.postUrl) }) { Text("Open on the site") }
                        }
                        // Not yet gone: back to a draft, so the writer can change it before it goes.
                        if (draft.state == PostState.Queued && draft.destination != Destination.Delete) {
                            TextButton(onClick = viewModel::takeBack) { Text("Edit") }
                        }
                    }
                }
            }
            if (state.vaultPhotos > 0) {
                VaultBanner(state.vaultPhotos, onChoose = { chooseVault.launch(null) }, onSkip = viewModel::skipVault)
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
            text.noteDate?.let { iso ->
                NoteDate(iso, editable && state.draft?.sentShas.isNullOrEmpty(), onDrop = viewModel::useThePublishDay)
            }
            TermRow("Categories", text.categories, editable, onAdd = { picker = TermKind.Category }, onRemove = { viewModel.remove(TermKind.Category, it) })
            TermRow("Tags", text.tags, editable, onAdd = { picker = TermKind.Tag }, onRemove = { viewModel.remove(TermKind.Tag, it) })
            // An edit from before the app kept front matter can't safely change it: shown read-only.
            val unknown = text.editingPath != null && text.extraFrontMatter == null
            MoreFrontMatter(
                text.extraFrontMatter.orEmpty(), viewModel.extraProblem, editable && !unknown, viewModel::setExtraFrontMatter,
                forceOpen = state.frontMatterBlocked != null, onOpened = viewModel::frontMatterShown,
                note = if (unknown) "Discard and open the post again to change its front matter." else null,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            TextField(
                value = TextFieldValue(text.body, viewModel.bodySelection, viewModel.bodyComposition),
                onValueChange = { viewModel.setBody(it) },
                placeholder = { Text("Write in Markdown…") },
                // A little more air between lines than Material's default: this is where the writing happens.
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                readOnly = !editable,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = plainField(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).testTag("body").onFocusChanged { bodyFocused = it.isFocused },
            )
        }
    }

    state.describing.firstOrNull()?.let { sitePath ->
        DescribePhoto(sitePath, onDone = { viewModel.describe(sitePath, it) })
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
    if (confirmDeleteFromBlog) {
        val title = text?.title?.trim().orEmpty().ifEmpty { "this post" }
        AlertDialog(
            onDismissRequest = { confirmDeleteFromBlog = false },
            title = { Text("Delete “$title” from the blog?") },
            text = {
                Text(
                    "One commit removes it from ${if (jekyllDraft) "_drafts" else "the site"}. " +
                        "Its photos stay, and the repository's history keeps the text. Changes you made here are lost.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDeleteFromBlog = false; viewModel.deleteFromBlog() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDeleteFromBlog = false }) { Text("Keep") } },
        )
    }
}

private fun deleteLabel(draft: Draft?) = when {
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
