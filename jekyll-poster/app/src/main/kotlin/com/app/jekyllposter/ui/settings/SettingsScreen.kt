package com.app.jekyllposter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.foundation.layout.Row as LayoutRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.BuildConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onSwitchBlog: () -> Unit, onSignOut: () -> Unit, onBlogPrivacy: () -> Unit = {}) {
    val account by container.accounts.account.collectAsStateWithLifecycle(null)
    val siteUrl by container.blogs.siteUrl.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading("Your blog")
            account?.let { a ->
                Row(a.repoName, "Branch ${a.branch}")
                siteUrl?.let { url -> Row(url, "The site", onClick = { uri.openUri(url) }) }
                Row("Switch blog", "Pick another repository this sign-in can write to", onClick = onSwitchBlog)
                Row("Blog & privacy", "Your commit email, the site's time zone, shared links, and what GitHub shows", onClick = onBlogPrivacy)
                Heading("Account")
                Row(
                    a.login,
                    if (a.refreshToken != null) "Signed in with GitHub" else "Signed in with a token",
                )
                Row("Sign out", "Drafts stay on this phone, for when you sign in to this blog again", onClick = onSignOut)
            }
            val scope = rememberCoroutineScope()
            Heading("Obsidian")
            val vault by container.settings.obsidianVault.collectAsStateWithLifecycle(null)
            val context = androidx.compose.ui.platform.LocalContext.current
            val chooseVault = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
                if (tree != null) scope.launch { com.app.jekyllposter.data.chooseVault(context, container.settings, tree) }
            }
            Row(
                vault?.let(::vaultName) ?: "Vault folder",
                if (vault != null) "Photos in shared notes are found here. Tap to choose another." else "Where photos in shared notes are found. Not chosen yet.",
                onClick = { chooseVault.launch(null) },
            )
            if (vault != null) {
                Row("Forget the vault folder", "The app stops reading it", onClick = {
                    scope.launch { com.app.jekyllposter.data.chooseVault(context, container.settings, null) }
                })
            }
            Heading("About")
            VersionRow(BuildConfig.VERSION_NAME)
        }
    }
}

/**
 * The app's version. A tap or a long press copies it, for a bug report: it names the build (the CI
 * run and commit, in a debug build), which a screenshot of the row can cut short. Both, so neither
 * gesture does nothing: TalkBack's double tap is a tap, and on e-ink a long press is slow to tell.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionRow(version: String) {
    val context = LocalContext.current
    val copy = {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Jekyll Poster version", "Jekyll Poster $version"))
        // Android 13 and later confirm a copy themselves; before that, nothing would.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Version copied", Toast.LENGTH_SHORT).show()
    }
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(role = Role.Button, onClickLabel = "Copy version", onLongClickLabel = "Copy version", onLongClick = copy, onClick = copy)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Jekyll Poster $version", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text("Posts to a Jekyll blog on GitHub Pages. Tap to copy the version.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    LayoutRow(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The whole row toggles; the switch only shows the state.
        Switch(checked = checked, onCheckedChange = null)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun Heading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
internal fun Row(title: String, detail: String, onClick: (() -> Unit)? = null, link: String? = null) {
    Column(
        // Read as one, title and detail, whether or not it does anything.
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(role = Role.Button, onClickLabel = link, onClick = onClick) else it.semantics(mergeDescendants = true) {} }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null && title == "Sign out") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // A row that leaves the app says so, looking like the link it is.
        if (link != null && onClick != null) {
            LayoutRow(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    link, style = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

/** A vault folder's own name, from its document tree (`primary:Documents/Notes` → Notes). */
private fun vaultName(tree: String): String = runCatching {
    android.provider.DocumentsContract.getTreeDocumentId(android.net.Uri.parse(tree)).substringAfter(':').trimEnd('/').substringAfterLast('/').ifEmpty { "Vault folder" }
}.getOrDefault("Vault folder")
