package com.app.jekyllposter.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.CachedPost
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.app.jekyllposter.ui.theme.LocalWhimsy
import com.app.jekyllposter.ui.theme.PosterFab
import com.app.jekyllposter.ui.theme.RowDivider
import com.app.jekyllposter.ui.theme.RowFrame
import com.app.jekyllposter.ui.theme.SectionHeading
import com.app.jekyllposter.ui.theme.TermPill
import androidx.compose.ui.semantics.contentDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenDraft: (Long) -> Unit, onSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.dismissError() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.siteTitle ?: "Your blog", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.account?.let { Text(it.repoName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Read the blog again") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menu = false; onSettings() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            PosterFab("New post", icon = { Icon(Icons.Default.Edit, null) }, onClick = { viewModel.newDraft(onOpenDraft) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (state.onPhone.isNotEmpty()) {
                    item { SectionHeading("On this phone") }
                    items(state.onPhone, key = { "d${it.id}" }) { draft ->
                        DraftRow(draft) { onOpenDraft(draft.id) }
                    }
                }
                item { SectionHeading("On your blog") }
                if (state.categories.size > 1) {
                    item { CategoryFilter(state.categories, state.category, viewModel::filter) }
                }
                if (state.onBlog.isEmpty()) {
                    item {
                        Text(
                            if (state.refreshing) "Reading your blog…" else LocalWhimsy.current.emptyBlog,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.onBlog, key = { "p${it.sha}${it.path}" }) { post ->
                    PostRow(post) { viewModel.edit(post, onOpenDraft) }
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(48.dp)) }
            }
        }
    }
}


@Composable
private fun DraftRow(draft: Draft, onClick: () -> Unit) {
    RowFrame(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(draft.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val whimsy = LocalWhimsy.current
        val (label, isError) = draft.status(whimsy.live)
        val shown = if (draft.state == PostState.Published && draft.buildState == BuildState.Live) label + whimsy.liveMark else label
        Text(
            shown, style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
    RowDivider()
}

/** A draft's state in a few words, and whether it needs the writer. */
fun Draft.status(live: String = "live on the site"): Pair<String, Boolean> = when (state) {
    PostState.Draft -> (if (editingPath != null) "Editing · not published yet" else "Draft") to false
    PostState.Queued -> "Waiting to publish…" to false
    PostState.Failed -> "Didn't publish: ${error.orEmpty()}" to true
    PostState.Published -> if (targetPath?.startsWith("_drafts/") == true) "Saved to the blog's _drafts" to false else when (buildState) {
        BuildState.Building -> "Published · the site is rebuilding…" to false
        BuildState.Live -> "Published · $live" to false
        BuildState.Failed -> "Published, but the site build failed. Check Actions on GitHub." to true
        BuildState.Unknown, null -> "Published to GitHub" to false
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PostRow(post: CachedPost, onClick: () -> Unit) {
    RowFrame(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(post.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val notes = listOfNotNull(
            post.date,
            if (post.path.contains("_drafts/")) "Jekyll draft" else null,
            if (!post.published) "Hidden" else null,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            post.categories.forEach { TermPill(it) }
        }
    }
    RowDivider()
}

/** The blog's categories as pills to filter the list by; the chosen one is outlined. */
@Composable
private fun CategoryFilter(categories: List<String>, chosen: String?, onChoose: (String?) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        item { TermPill("All", selected = chosen == null, onClick = { onChoose(null) }) }
        items(categories) { c -> TermPill(c, selected = c.equals(chosen, ignoreCase = true), onClick = { onChoose(c) }) }
    }
}
