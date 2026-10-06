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
    var confirmZone by remember { mutableStateOf<String?>(null) }
    var confirmAnonymous by remember { mutableStateOf(false) }
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
            Heading("Writing anonymously")
            when {
                state.anonymous -> Row("All set for writing anonymously", "Every switch below that keeps your name, place and habits off the blog is on.")
                state.settingUpAnonymous -> Row("Setting up…", "Turning on the switches below.")
                else -> Row(
                    "Set up for writing anonymously",
                    "Turns on the switches below that keep your name, place and habits off the blog. See also “Beyond the app”, at the end.",
                    onClick = { confirmAnonymous = true },
                )
            }

            Heading("Your connection")
            Toggle(
                "Only connect through a VPN",
                when {
                    !state.onlyThroughVpn -> "Off: the app reaches GitHub over whatever connection the phone has, and GitHub sees where you are."
                    state.waitingForVpn -> "No VPN connection, so nothing is sent to GitHub. Posts you publish wait for it. " +
                        "A VPN that leaves this app out (split tunneling), or carries only some addresses, counts as none."
                    else -> "GitHub and your blog's site see your VPN's address, not yours. If the VPN drops, nothing is sent until it's back."
                },
                state.onlyThroughVpn,
                viewModel::setOnlyThroughVpn,
            )

            Heading("Your commits")
            Toggle(
                "Commit with your no-reply email",
                if (state.commitAsNoReply) "Commits from this app are signed ${state.login ?: "with your login"} <${state.noReplyEmail ?: "your GitHub no-reply address"}>, never your profile's name."
                else "Off: GitHub's default for your account, your email, or your no-reply address if GitHub is set to keep your email private.",
                state.commitAsNoReply,
                viewModel::setCommitAsNoReply,
            )
            Toggle(
                "Plain commit messages",
                if (state.plainCommitMessages) "Commits say “Update blog”, not the post's title. The file's name and text still say what it is."
                else "Off: commits name the post, “Add post: Title”. The title stays in the history, even after the post is deleted.",
                state.plainCommitMessages,
                viewModel::setPlainCommitMessages,
            )

            Heading("When posts go out")
            Toggle(
                "Send at a random time",
                if (state.sendAtRandomTime) "Posts go out at a random moment in the three hours after you tap Publish, so their times don't trace your day. Deleting is never delayed."
                else "Off: posts go out as soon as you tap Publish, and GitHub shows the minute.",
                state.sendAtRandomTime,
                viewModel::setSendAtRandomTime,
            )

            Heading("Dates")
            Toggle(
                "Date posts by the day only",
                if (state.datesByDayOnly) "New posts say the day, not the time you posted it or your time zone."
                else "Off: new posts say the minute they went out, and a time zone.",
                state.datesByDayOnly,
                viewModel::setDatesByDayOnly,
            )
            val zone = state.siteZone
            Row(
                zone ?: "No time zone set",
                if (zone != null) "Posts are dated in the site's time zone, wherever you write them."
                else "Posts carry your phone's time zone, so a post written while travelling says where you were.",
            )
            // UTC first: a zone's name in _config.yml (Europe/Lisbon) says more about where you
            // are than any post's date does.
            // `Etc/UTC` or `GMT` is UTC already: offering it again would commit a change to nothing.
            for (option in listOf(UTC, state.phoneZone).distinctBy(::sameZone).filter { sameZone(it) != zone?.let(::sameZone) }) {
                Row(
                    if (state.zoneBeingSet == option) "Setting the time zone…" else "Use $option",
                    if (option == UTC) "Dates won't say where you are. Saved in the blog's _config.yml."
                    else "This phone's time zone, saved in the blog's _config.yml, where anyone can read it.",
                    onClick = if (state.settingZone) null else ({ confirmZone = option }),
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

            // What no switch can do: the writer's own choices around the app.
            Heading("Beyond the app")
            Row("A GitHub account of its own", "Made with an email and a name that aren't yours. Its login is in the blog's address and in every commit.")
            Row("Sign in in a private tab", "When the app opens GitHub, use a private tab signed in only to that account, so GitHub doesn't see it beside your own.")
            Row("Free sites are public repositories", "Anyone can read their history. A private one needs a paid plan, in your name.")
            Row("Your own domain", "Whoever sells it knows who paid, and shows it to anyone who asks unless it's kept private.")
            Row("Your theme", "Analytics or comments in your theme see you when you open your own posts.")
        }
    }
    if (confirmAnonymous) {
        AlertDialog(
            onDismissRequest = { confirmAnonymous = false },
            title = { Text("Set up for writing anonymously?") },
            text = {
                Text(
                    "This turns on:\n" +
                        "• Only connect through a VPN\n" +
                        "• Commit with your no-reply email\n" +
                        "• Plain commit messages\n" +
                        "• Send at a random time\n" +
                        "• Date posts by the day only\n" +
                        "• Remove tracking codes\n\n" +
                        "It needs a VPN app: without one connected, nothing is sent to GitHub. " +
                        "Each can be turned off again here. The site's time zone is left as it is: choose UTC below if you like.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmAnonymous = false; viewModel.writeAnonymously() }) { Text("Turn them on") } },
            dismissButton = { TextButton(onClick = { confirmAnonymous = false }) { Text("Not now") } },
        )
    }
    confirmZone?.let { chosen ->
        AlertDialog(
            onDismissRequest = { confirmZone = null },
            title = { Text("Set the site's time zone to $chosen?") },
            text = {
                Text(
                    "Jekyll will date every post in it. A post written near midnight can move to the other day, " +
                        "and so can its address, if your addresses have the date in them. One commit to _config.yml.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmZone = null; viewModel.useZone(chosen) }) { Text("Set it") } },
            dismissButton = { TextButton(onClick = { confirmZone = null }) { Text("Keep") } },
        )
    }
}

private const val UTC = "UTC"

/** [id], or [UTC] for any name of it. */
private fun sameZone(id: String): String =
    if (runCatching { java.time.ZoneId.of(id).normalized() == java.time.ZoneOffset.UTC }.getOrDefault(false)) UTC else id
