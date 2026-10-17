package com.app.jekyllposter.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.core.github.GitHubRepo
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.withTokens
import com.app.jekyllposter.ui.catching
import com.app.jekyllposter.ui.forWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
            } catch (e: CancellationException) {
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
        get() = if (deviceTokens == null && !addingToGitHubSignIn) null else container.githubAppSlug.takeIf { it.isNotBlank() }?.let { "https://github.com/apps/$it/installations/new" }

    fun setToken(token: String) = _state.update { it.copy(token = token, error = null) }

    fun checkToken() {
        val token = _state.value.token.trim()
        if (token.isEmpty()) return
        deviceTokens = null
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                listRepos(token)
            } catch (e: CancellationException) {
                throw e
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

    /**
     * The sign-in a blog is being added to, from the blogs the app has; null when signing in. Its
     * list's Back leaves to where it came from, not to a sign-in screen.
     */
    var addingTo: Long? = null
        private set

    private var addingToGitHubSignIn = false

    fun back() = _state.update { it.copy(repos = null, error = null) }

    /** Lists the repositories sign-in [signInId] can write to, to add one as a blog. */
    fun addBlogTo(signInId: Long) {
        addingTo = signInId
        _state.update { it.copy(busy = true, error = null, repos = emptyList()) }
        viewModelScope.launch {
            val account = container.accounts.forSignIn(signInId) ?: return@launch _state.update { it.copy(busy = false, repos = null) }
            addingToGitHubSignIn = account.refreshToken != null
            try {
                listRepos(account.token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Stays on the blog list with the error; no token is put in a field to resubmit.
                _state.update { it.copy(busy = false, error = e.forWriter()) }
            }
        }
    }

    fun choose(repo: GitHubRepo) {
        val s = _state.value
        val login = s.login ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val client = container.client(s.token.trim())
            val siteUrl = catching { client.pages(repo.owner.login, repo.name)?.htmlUrl }.getOrNull()
            // Adding keeps the sign-in as stored now (it may have been renewed while the list was
            // open), not the token the list was read with.
            val adding = addingTo
            if (adding != null) {
                if (!container.accounts.addBlog(adding, repo.owner.login, repo.name, repo.defaultBranch, siteUrl)) {
                    return@launch _state.update { it.copy(busy = false, error = "That sign-in was removed meanwhile.") }
                }
            } else {
                val fresh = Account(login, s.token.trim(), repo.owner.login, repo.name, repo.defaultBranch, siteUrl)
                // Expiry counts from when the token arrived, not from when a blog was picked.
                container.accounts.save(deviceTokens?.let { fresh.withTokens(it, tokensAt) } ?: fresh)
            }
            // After the switch: a read of the blog left, finishing now, can't put its title back.
            container.blogs.clear()
            // A first read fills the category picker; if it fails, Home tries again.
            catching { container.blogs.refresh() }
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
