package com.app.jekyllposter.ui.connect

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.core.github.GitHubRepo

@Composable
fun ConnectScreen(viewModel: ConnectViewModel, onCancel: () -> Unit = {}, onConnected: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onConnected() }
    Scaffold { padding ->
        val repos = state.repos
        val code = state.deviceCode
        if (code != null) {
            BackHandler(onBack = viewModel::cancelSignIn)
            CodeStep(code, viewModel::cancelSignIn, Modifier.padding(padding))
        } else if (repos == null) {
            TokenStep(state, viewModel, Modifier.padding(padding))
        } else {
            val back = if (viewModel.switching) onCancel else viewModel::back
            BackHandler(onBack = back)
            RepoStep(state.login.orEmpty(), repos, state.busy, state.error, viewModel.installUrl, viewModel::choose, back, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TokenStep(state: ConnectViewModel.State, viewModel: ConnectViewModel, modifier: Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.padding(top = 24.dp))
        Text("Post to your Jekyll blog", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            "Write on your phone; the app commits each post to your blog's GitHub repository, and GitHub Pages publishes it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (viewModel.signInWithGitHubAvailable) {
            Button(onClick = viewModel::signInWithGitHub, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign in with GitHub") }
            Text("Or use a token you make yourself:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Connect with a token", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            "Make a fine-grained token on GitHub for just your blog's repository. The link fills in the permissions: " +
                "Contents (read and write) to commit posts, and Actions (read) to tell you when the site has rebuilt.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(
            onClick = { uri.openUri(ConnectViewModel.NEW_TOKEN_URL) },
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        ) { Text("Make a token on GitHub") }
        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::setToken,
            label = { Text("Token") },
            placeholder = { Text("github_pat_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { viewModel.checkToken() }),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::checkToken, enabled = state.token.isNotBlank() && !state.busy, modifier = Modifier.fillMaxWidth()) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(18.dp).semantics { contentDescription = "Checking the token" }, strokeWidth = 2.dp)
            } else {
                Text("Continue")
            }
        }
        Text(
            "The token stays on this phone, sealed with a key that never leaves it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeStep(code: DeviceFlow.Code, onCancel: () -> Unit, modifier: Modifier) {
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.padding(top = 24.dp))
        Text("Enter this code on GitHub", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(
            code.userCode,
            style = MaterialTheme.typography.displaySmall,
            // Read out letter by letter: as a word it can't be typed back.
            modifier = Modifier.semantics { contentDescription = "Code: " + code.userCode.toList().joinToString(" ") },
        )
        Button(onClick = { clipboard.setText(AnnotatedString(code.userCode)); uri.openUri(code.verificationUri) }, modifier = Modifier.fillMaxWidth()) {
            Text("Copy the code and open GitHub")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Waiting for you to approve it on GitHub…", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun RepoStep(
    login: String,
    repos: List<GitHubRepo>,
    busy: Boolean,
    error: String?,
    installUrl: String?,
    onChoose: (GitHubRepo) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Which blog?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("Signed in as $login. Pick the repository your Jekyll blog lives in.", style = MaterialTheme.typography.bodyLarge)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (busy) {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading your blog…")
            }
        } else if (repos.isEmpty()) {
            val uri = LocalUriHandler.current
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (installUrl != null) {
                    Text("Jekyll Poster isn't installed on any repository you can write to yet. Install it on your blog's repository, then come back.")
                    Button(onClick = { uri.openUri(installUrl) }) { Text("Install on GitHub") }
                    TextButton(onClick = onBack) { Text("Back") }
                } else {
                    Text("This token can't write to any repository. On GitHub, give it your blog's repository and Contents: read and write.")
                    TextButton(onClick = onBack) { Text("Use a different token") }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(repos, key = { it.fullName }) { repo ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onChoose(repo) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    ) {
                        Text(repo.fullName, style = MaterialTheme.typography.titleMedium)
                        val notes = listOfNotNull(if (repo.hasPages) "GitHub Pages" else null, if (repo.private) "Private" else null, "branch ${repo.defaultBranch}")
                        Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
