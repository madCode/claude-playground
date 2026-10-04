package com.app.jekyllposter.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.github.GitHubRepo
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.withTokens
import kotlinx.coroutines.Job
import com.app.jekyllposter.ui.forWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val token: String = "",
        val login: String? = null,
        /** Null until the token is checked; then the repositories it can write to. */
        val repos: List<GitHubRepo>? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
        /** While signing in with GitHub: the code to enter on github.com. */
        val deviceCode: DeviceFlow.Code? = null,
    )

    val signInWithGitHubAvailable: Boolean get() = container.deviceFlow != null

    /** Tokens from "Sign in with GitHub", kept until a blog is picked, and when they arrived. */
    private var deviceTokens: DeviceFlow.Tokens? = null
    private var tokensAt = 0L
    private var waiting: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Starts "Sign in with GitHub": shows a code, then waits for the writer to enter it on GitHub. */
    fun signInWithGitHub() {
        val flow = container.deviceFlow ?: return
        _state.update { it.copy(busy = true, error = null) }
        waiting?.cancel()
        waiting = viewModelScope.launch {
            try {
                val code = flow.start()
                _state.update { it.copy(busy = false, deviceCode = code) }
                val tokens = flow.await(code)
                deviceTokens = tokens
                tokensAt = System.currentTimeMillis()
                _state.update { it.copy(deviceCode = null, busy = true) }
                listRepos(tokens.accessToken)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, deviceCode = null, error = e.forWriter()) }
            }
        }
    }

    fun cancelSignIn() {
        waiting?.cancel()
        _state.update { it.copy(deviceCode = null, busy = false) }
    }

    /** github.com's page for installing the app on a repository, when the build names the app. */
    val installUrl: String?
        get() = if (deviceTokens == null) null else container.githubAppSlug.takeIf { it.isNotBlank() }?.let { "https://github.com/apps/$it/installations/new" }

    fun setToken(token: String) = _state.update { it.copy(token = token, error = null) }

    fun checkToken() {
        val token = _state.value.token.trim()
        if (token.isEmpty()) return
        deviceTokens = null
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                listRepos(token)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.forWriter()) }
            }
        }
    }

    private suspend fun listRepos(token: String) {
        val client = container.client(token)
        val user = client.user()
        // Pages sites first: they're the likely blogs.
        val repos = client.writableRepos().sortedByDescending { it.hasPages }
        _state.update { it.copy(login = user.login, repos = repos, busy = false, token = token) }
    }

    fun back() = _state.update { it.copy(repos = null, error = null) }

    /** Lists the blogs the current sign-in can write to, to switch to another. */
    fun switchBlog() {
        _state.update { it.copy(busy = true, error = null, repos = emptyList()) }
        viewModelScope.launch {
            val account = container.accounts.current() ?: return@launch _state.update { it.copy(busy = false, repos = null) }
            if (account.refreshToken != null) {
                deviceTokens = DeviceFlow.Tokens(account.token, account.expiresAt?.let { (it - System.currentTimeMillis()) / 1000 }, account.refreshToken)
                tokensAt = System.currentTimeMillis()
            }
            try {
                listRepos(account.token)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, repos = null, token = account.token, error = e.forWriter()) }
            }
        }
    }

    fun choose(repo: GitHubRepo) {
        val s = _state.value
        val login = s.login ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val client = container.client(s.token.trim())
            val siteUrl = runCatching { client.pages(repo.owner.login, repo.name)?.htmlUrl }.getOrNull()
            container.blogs.clear()
            val account = Account(login, s.token.trim(), repo.owner.login, repo.name, repo.defaultBranch, siteUrl)
            // Expiry counts from when the token arrived, not from when a blog was picked.
            container.accounts.save(deviceTokens?.let { account.withTokens(it, tokensAt) } ?: account)
            // A first read fills the category picker; if it fails, Home tries again.
            runCatching { container.blogs.refresh() }
            _state.update { it.copy(busy = false, done = true) }
        }
    }

    companion object {
        /**
         * GitHub's new fine-grained token page, filled in with a name and the two permissions the
         * app needs: Contents to write posts, Actions to see the site build. The writer still picks
         * the repository.
         */
        const val NEW_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new" +
            "?name=Jekyll%20Poster&description=Posting%20to%20my%20blog%20from%20my%20phone&contents=write&actions=read"
    }
}
