package com.app.jekyllposter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * What the blog shows beyond its posts, and the switches for it. Every default is what GitHub
 * and Jekyll do on their own; each switch is one step more private.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogPrivacyScreen(viewModel: BlogPrivacyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var confirmZone by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    val github = state.repoName?.let { "https://github.com/$it" }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blog & privacy") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Heading("Your commits")
            Toggle(
                "Commit with your no-reply email",
                if (state.commitAsNoReply) "Commits from this app show ${state.noReplyEmail ?: "your GitHub no-reply address"}."
                else "Off: GitHub's default for your account, your email, or your no-reply address if GitHub is set to keep your email private.",
                state.commitAsNoReply,
                viewModel::setCommitAsNoReply,
            )

            Heading("Dates")
            val zone = state.siteZone
            Row(
                zone ?: "No time zone set",
                if (zone != null) "Posts are dated in the site's time zone, wherever you write them."
                else "Posts carry your phone's time zone, so a post written while travelling says where you were.",
            )
            if (zone != state.phoneZone) {
                Row(
                    if (state.settingZone) "Setting the time zone…" else "Use ${state.phoneZone}",
                    "This phone's time zone, saved in the blog's _config.yml",
                    onClick = if (state.settingZone) null else ({ confirmZone = true }),
                )
            }

            Heading("Shared links")
            Toggle(
                "Remove tracking codes",
                "Links shared to the app lose utm_ tags, fbclid and the like, which tell sites who shared them. " +
                    "Done before the editor opens, so you see the links as they'll be published.",
                state.removeTrackingCodes,
                viewModel::setRemoveTrackingCodes,
            )

            Heading("On GitHub")
            Row(
                when (state.visibility) {
                    Visibility.Public -> "The repository is public"
                    Visibility.Private -> "The repository is private"
                    Visibility.Loading, Visibility.Unknown -> "Repository visibility"
                },
                when (state.visibility) {
                    Visibility.Public -> "Anyone can read it on GitHub, including _drafts and every earlier version of a post."
                    Visibility.Private -> "Only people you give access can read it, though the site itself is public."
                    Visibility.Loading -> "Asking GitHub…"
                    Visibility.Unknown -> "Couldn't read it from GitHub just now."
                },
                onClick = github?.let { { uri.openUri("$it/settings") } },
                link = "Open on GitHub",
            )
            Row(
                "Earlier versions stay",
                "Every edit and deleted post stays in the repository's history.",
                onClick = github?.let { { uri.openUri("$it/commits/${state.branch ?: ""}") } },
                link = "Open the history on GitHub",
            )
        }
    }
    if (confirmZone) {
        AlertDialog(
            onDismissRequest = { confirmZone = false },
            title = { Text("Set the site's time zone to ${state.phoneZone}?") },
            text = {
                Text(
                    "Jekyll will date every post in it. A post written near midnight can move to the other day, " +
                        "and so can its address, if your addresses have the date in them. One commit to _config.yml.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmZone = false; viewModel.useThisPhonesZone() }) { Text("Set it") } },
            dismissButton = { TextButton(onClick = { confirmZone = false }) { Text("Keep") } },
        )
    }
}
