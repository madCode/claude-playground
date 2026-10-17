package com.app.jekyllposter.ui.blogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.data.BlogEntry
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.data.Removal
import com.app.jekyllposter.ui.theme.RowDivider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A blog as the switcher lists it: open or not, and what of the writer's waits for it. */
data class BlogRow(val entry: BlogEntry, val open: Boolean, val status: String)

/** A sign-in and the blogs it posts with. */
data class SignInGroup(val signInId: Long, val login: String, val withGitHub: Boolean, val blogs: List<BlogRow>) {
    val how: String get() = if (withGitHub) "with GitHub" else "signed in with a token"
}

/** [entries] grouped by sign-in, each with what of [drafts] is for it. */
fun groups(entries: List<BlogEntry>, openKey: String?, drafts: List<Draft>): List<SignInGroup> =
    entries.groupBy { it.signInId }.map { (id, blogs) ->
        val first = blogs.first()
        SignInGroup(id, first.login, first.withGitHub, blogs.map { BlogRow(it, it.key == openKey, status(it, drafts.filter { d -> d.blog == it.key })) })
    }

/**
 * The repository, then the blog's posts on the phone. A post that didn't go is named here, since
 * it's hidden from Home while another blog is open.
 */
private fun status(blog: BlogEntry, drafts: List<Draft>): String {
    fun count(n: Int, one: String, many: String = one) = if (n == 0) null else "$n ${if (n == 1) one else many}"
    return listOfNotNull(
        blog.repoName,
        count(drafts.count { it.state == PostState.Draft && !it.untouched }, "draft", "drafts"),
        count(drafts.count { it.state == PostState.Queued }, "waiting to go"),
        count(drafts.count { it.state == PostState.Failed }, "didn't go"),
    ).joinToString(" · ")
}

/** The writer's blogs and sign-ins: switching, and removing a sign-in. */
class BlogsViewModel(private val container: AppContainer) : ViewModel() {
    /** A sign-in the writer asked to remove; [waiting] once removing it was refused. */
    data class Removing(val group: SignInGroup, val waiting: List<Draft>? = null)

    data class State(
        val groups: List<SignInGroup> = emptyList(),
        val removing: Removing? = null,
        /** The last sign-in was removed: nothing is left to post to. */
        val signedOut: Boolean = false,
    ) {
        val blogCount: Int get() = groups.sumOf { it.blogs.size }
    }

    private val removing = MutableStateFlow<Removing?>(null)
    private val signedOut = MutableStateFlow(false)

    val state: StateFlow<State> =
        combine(container.accounts.blogs, container.accounts.currentKey, container.drafts.all(), removing, signedOut) { blogs, open, drafts, r, out ->
            State(groups(blogs, open, drafts), r, out)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /**
     * Opens blog [key]. In the app's scope: the screen that asked is closing, and the open blog's
     * first read mustn't go with it.
     */
    fun open(key: String) {
        container.appScope.launch { container.open(key) }
    }

    fun askRemove(group: SignInGroup) = removing.update { Removing(group) }

    fun cancelRemove() = removing.update { null }

    fun remove() {
        val asked = removing.value ?: return
        // The app's scope: Back right after Remove mustn't cancel it.
        container.appScope.launch {
            when (val result = container.remove(asked.group.signInId)) {
                is Removal.Refused -> removing.value = asked.copy(waiting = result.waiting)
                is Removal.Removed -> {
                    removing.value = null
                    if (container.accounts.currentKey() == null) signedOut.value = true
                }
            }
        }
    }

    /** Sends the posts that stopped a sign-in's removal now, not at their random times. */
    fun sendWaitingNow() {
        val waiting = removing.value?.waiting.orEmpty()
        removing.value = null
        container.appScope.launch { waiting.forEach { container.sendNow(it.id) } }
    }
}

/** Every blog under its sign-in, the open one ticked. [onRemove] adds Remove to each sign-in. */
@Composable
fun BlogList(groups: List<SignInGroup>, onOpen: (BlogRow) -> Unit, onRemove: ((SignInGroup) -> Unit)? = null) {
    Column {
        groups.forEach { group ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${group.login} · ${group.how}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                onRemove?.let { remove ->
                    TextButton(onClick = { remove(group) }, modifier = Modifier.semantics { contentDescription = "Remove @${group.login}" }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            group.blogs.forEach { row ->
                Row(
                    Modifier.fillMaxWidth()
                        .semantics { selected = row.open }
                        .clickable(role = Role.Button, onClickLabel = if (row.open) null else "Switch to this blog") { onOpen(row) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.entry.title ?: row.entry.repo, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(row.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.open) Icon(Icons.Default.Check, "Open now", tint = MaterialTheme.colorScheme.primary)
                }
                RowDivider()
            }
        }
    }
}

/**
 * Add a blog and Add a sign-in. A blog is added from one sign-in's repositories: with more than
 * one, the writer picks which first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddButtons(groups: List<SignInGroup>, onAddBlog: (Long) -> Unit, onAddSignIn: () -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (groups.isNotEmpty()) {
            OutlinedButton(
                onClick = { if (groups.size == 1) onAddBlog(groups.single().signInId) else picking = true },
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
            ) { Text("+ Add a blog") }
        }
        OutlinedButton(onClick = onAddSignIn, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)) { Text("+ Add a sign-in") }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add a blog with which sign-in?") },
            text = {
                Column {
                    groups.forEach { group ->
                        Text(
                            "@${group.login} · ${group.how}",
                            Modifier.fillMaxWidth().clickable(role = Role.Button) { picking = false; onAddBlog(group.signInId) }.padding(vertical = 14.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

/** Asks before removing a sign-in, or says why it can't be removed yet. */
@Composable
fun RemoveDialog(removing: BlogsViewModel.Removing, onRemove: () -> Unit, onSendNow: () -> Unit, onCancel: () -> Unit) {
    val group = removing.group
    val waiting = removing.waiting
    if (waiting != null) {
        val blogs = group.blogs.associate { it.entry.key to (it.entry.title ?: it.entry.repo) }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Posts are still to go") },
            text = {
                Text(
                    "These are waiting to go out with @${group.login}, and would fail without it:\n\n" +
                        waiting.joinToString("\n") { "• “${it.title.ifBlank { "Untitled" }}” to ${blogs[it.blog] ?: it.blog}" } +
                        "\n\nSend them now, or open their blog and Stop and edit them. Then remove the sign-in.",
                )
            },
            confirmButton = { TextButton(onClick = onSendNow) { Text("Send them now") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Keep the sign-in") } },
        )
        return
    }
    val names = group.blogs.joinToString(", ") { it.entry.title ?: it.entry.repo }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Remove @${group.login}?") },
        text = { Text("The app forgets this sign-in and its blogs: $names. Their drafts stay on this phone, for when you add them again.") },
        confirmButton = { TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Settings → Your blogs: every blog to switch to, add or remove. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogsScreen(viewModel: BlogsViewModel, onBack: () -> Unit, onOpened: () -> Unit, onAddBlog: (Long) -> Unit, onAddSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your blogs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            BlogList(state.groups, onOpen = { if (!it.open) viewModel.open(it.entry.key); onOpened() }, onRemove = viewModel::askRemove)
            AddButtons(state.groups, onAddBlog, onAddSignIn)
            Text(
                "Removing a sign-in keeps its drafts on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
    state.removing?.let { RemoveDialog(it, viewModel::remove, viewModel::sendWaitingNow, viewModel::cancelRemove) }
}
