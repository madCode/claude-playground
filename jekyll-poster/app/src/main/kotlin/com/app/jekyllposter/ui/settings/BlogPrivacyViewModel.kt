package com.app.jekyllposter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.core.jekyll.ConfigEdit
import com.app.jekyllposter.ui.forWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId

/** What the blog and the app tell GitHub and readers beyond the posts, and the switches for it. */
class BlogPrivacyViewModel(private val container: AppContainer, private val phoneZone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {
    data class State(
        val repoName: String? = null,
        val branch: String? = null,
        /** `<id>+<login>@users.noreply.github.com`, once the account is read. */
        val noReplyEmail: String? = null,
        val commitAsNoReply: Boolean = false,
        val removeTrackingCodes: Boolean = false,
        /** The site's `timezone:` from `_config.yml`; null when it sets none. */
        val siteZone: String? = null,
        val phoneZone: String = "",
        val settingZone: Boolean = false,
        /** Null until GitHub has said. */
        val public: Boolean? = null,
        val message: String? = null,
    )

    private val local = MutableStateFlow(State(phoneZone = phoneZone().id))

    val state: StateFlow<State> = combine(
        local, container.settings.commitAsNoReply, container.settings.removeTrackingCodes, container.blogs.config, container.accounts.account,
    ) { s, noReply, removeTracking, config, account ->
        s.copy(
            commitAsNoReply = noReply, removeTrackingCodes = removeTracking, siteZone = config.timezone?.id,
            repoName = account?.repoName, branch = account?.branch,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    init {
        viewModelScope.launch {
            val blog = container.blogs.blog() ?: return@launch
            runCatching { blog.user() }.getOrNull()?.let { user -> local.update { it.copy(noReplyEmail = user.noReplyAuthor.email) } }
            runCatching { blog.repository() }.getOrNull()?.let { repo -> local.update { it.copy(public = !repo.private) } }
        }
    }

    fun setCommitAsNoReply(on: Boolean) = viewModelScope.launch { container.settings.setCommitAsNoReply(on) }

    fun setRemoveTrackingCodes(on: Boolean) = viewModelScope.launch { container.settings.setRemoveTrackingCodes(on) }

    /**
     * Sets the site's time zone to the phone's: one commit changing only `_config.yml`'s
     * `timezone:` line, and only if the file is still as read.
     */
    fun useThisPhonesZone() {
        if (local.value.settingZone) return
        local.update { it.copy(settingZone = true, message = null) }
        viewModelScope.launch {
            val zone = local.value.phoneZone
            val message = try {
                val blog = container.blogs.blog() ?: error("Sign in to change the blog.")
                val current = blog.file(CONFIG)
                val author = if (container.settings.commitAsNoReply()) blog.user().noReplyAuthor else null
                blog.commit(
                    "Set the site's time zone to $zone",
                    listOf(FileChange.text(CONFIG, ConfigEdit.withTimezone(current?.text, zone))),
                    mapOf(CONFIG to current?.sha), author,
                )
                container.blogs.refresh()
                "The site's time zone is now $zone. The site rebuilds in a minute or two."
            } catch (e: GitHubException) {
                if (e.kind == GitHubException.Kind.Changed) "_config.yml changed on GitHub just now. Try again." else e.forWriter()
            } catch (e: Exception) {
                e.message ?: "Couldn't change the time zone."
            }
            local.update { it.copy(settingZone = false, message = message) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private companion object {
        const val CONFIG = "_config.yml"
    }
}
