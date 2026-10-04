package com.app.jekyllposter.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.BuildConfig

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
            Heading("About")
            Row("Jekyll Poster ${BuildConfig.VERSION_NAME}", "Posts to a Jekyll blog on GitHub Pages")
        }
    }
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
internal fun Row(title: String, detail: String, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null && title == "Sign out") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}
