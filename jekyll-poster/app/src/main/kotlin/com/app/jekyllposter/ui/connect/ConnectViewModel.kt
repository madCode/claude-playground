package com.app.jekyllposter.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.github.GitHubRepo
import com.app.jekyllposter.data.Account
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
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun setToken(token: String) = _state.update { it.copy(token = token, error = null) }

    fun checkToken() {
        val token = _state.value.token.trim()
        if (token.isEmpty()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val client = container.client(token)
                val user = client.user()
                // Pages sites first: they're the likely blogs.
                val repos = client.writableRepos().sortedByDescending { it.hasPages }
                _state.update { it.copy(login = user.login, repos = repos, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.forWriter()) }
            }
        }
    }

    fun back() = _state.update { it.copy(repos = null, error = null) }

    fun choose(repo: GitHubRepo) {
        val s = _state.value
        val login = s.login ?: return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val client = container.client(s.token.trim())
            val siteUrl = runCatching { client.pages(repo.owner.login, repo.name)?.htmlUrl }.getOrNull()
            container.blogs.clear()
            container.accounts.save(Account(login, s.token.trim(), repo.owner.login, repo.name, repo.defaultBranch, siteUrl))
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
