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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/** What the blog and the app tell GitHub and readers beyond the posts, and the switches for it. */
enum class Visibility { Loading, Public, Private, Unknown }

class BlogPrivacyViewModel(private val container: AppContainer, private val phoneZone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {
    data class State(
        val repoName: String? = null,
        val branch: String? = null,
        /** `<id>+<login>@users.noreply.github.com`, once the account is read. */
        val noReplyEmail: String? = null,
        val commitAsNoReply: Boolean = false,
        val removeTrackingCodes: Boolean = false,
        val datesByDayOnly: Boolean = false,
        val sendAtRandomTime: Boolean = false,
        val plainCommitMessages: Boolean = false,
        /** The account's login, which no-reply commits are signed with. */
        val login: String? = null,
        val onlyThroughVpn: Boolean = false,
        /** Asked for, and there's no VPN: nothing is going out. */
        val waitingForVpn: Boolean = false,
        /** The site's `timezone:` from `_config.yml`; null when it sets none. */
        val siteZone: String? = null,
        val phoneZone: String = "",
        val settingZone: Boolean = false,
        /** The zone being set, while [settingZone]. */
        val zoneBeingSet: String? = null,
        val settingUpAnonymous: Boolean = false,
        /** Set here and landed, shown while the blog as last read still has [zoneBefore]. */
        val committedZone: String? = null,
        val zoneBefore: String? = null,
        val visibility: Visibility = Visibility.Loading,
        val message: String? = null,
    ) {
        /** Every switch for writing anonymously is on. */
        val anonymous: Boolean
            get() = onlyThroughVpn && commitAsNoReply && plainCommitMessages && sendAtRandomTime && datesByDayOnly && removeTrackingCodes
    }

    private val local = MutableStateFlow(State(phoneZone = phoneZone().id))

    val state: StateFlow<State> = combine(
        local, container.settings.commitAsNoReply, container.settings.removeTrackingCodes, container.blogs.config, container.accounts.account,
    ) { s, noReply, removeTracking, config, account ->
        s.copy(
            commitAsNoReply = noReply, removeTrackingCodes = removeTracking, siteZone = s.committedZone?.takeIf { config.timezone?.id == s.zoneBefore } ?: config.timezone?.id,
            repoName = account?.repoName, branch = account?.branch, login = account?.login,
        )
    }.combine(
        combine(
            container.settings.onlyThroughVpn, container.waitingForVpn, container.settings.datesByDayOnly,
            container.settings.sendAtRandomTime, container.settings.plainCommitMessages,
        ) { only, waiting, dayOnly, random, plain ->
            { s: State -> s.copy(onlyThroughVpn = only, waitingForVpn = waiting, datesByDayOnly = dayOnly, sendAtRandomTime = random, plainCommitMessages = plain) }
        },
    ) { s, withSwitches -> withSwitches(s) }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    init {
        viewModelScope.launch {
            val blog = container.blogs.blog()
            val user = blog?.let { b -> runCatching { b.user() }.getOrNull() }
            local.update { it.copy(noReplyEmail = user?.noReplyAuthor?.email) }
            val repo = blog?.let { b -> runCatching { b.repository() }.getOrNull() }
            local.update { it.copy(visibility = repo?.let { r -> if (r.private) Visibility.Private else Visibility.Public } ?: Visibility.Unknown) }
        }
    }

    /**
     * On, the account's no-reply address is looked up now and kept, so publishing needs no extra
     * call; if it can't be, the switch stays off and says why.
     */
    private var switching: kotlinx.coroutines.Job? = null

    fun setCommitAsNoReply(on: Boolean) {
        // The last tap wins: an "on" still looking up the address mustn't land after an "off".
        switching?.cancel()
        switching = viewModelScope.launch {
            val account = container.accounts.current() ?: return@launch
            if (!on) return@launch container.settings.setCommitAsNoReply(account.login, null)
            val author = runCatching { container.blogs.blog()?.user()?.noReplyAuthor }.getOrNull()
            ensureActive()
            if (author == null) {
                // The VPN switch on and no VPN: say so, or "try again" would fail the same way.
                val why = if (container.waitingForVpn.first()) "Connect your VPN, then try again: nothing goes to GitHub without it."
                else "Try again."
                local.update { it.copy(message = "Couldn't find your GitHub no-reply address just now. $why") }
                return@launch
            }
            container.settings.setCommitAsNoReply(account.login, author)
            local.update { it.copy(noReplyEmail = author.email) }
        }
    }

    fun setRemoveTrackingCodes(on: Boolean) = viewModelScope.launch { container.settings.setRemoveTrackingCodes(on) }

    fun setDatesByDayOnly(on: Boolean) = viewModelScope.launch { container.settings.setDatesByDayOnly(on) }

    /**
     * Turns on every switch for writing anonymously. The no-reply address is looked up first:
     * with the VPN switch on and no VPN yet, it couldn't be.
     */
    fun writeAnonymously() {
        if (local.value.settingUpAnonymous) return
        local.update { it.copy(settingUpAnonymous = true) }
        // In the screen's scope, as each switch is: left half way, the row still offers to finish.
        viewModelScope.launch {
            try {
                with(container.settings) {
                    setPlainCommitMessages(true)
                    setSendAtRandomTime(true)
                    setDatesByDayOnly(true)
                    setRemoveTrackingCodes(true)
                }
                switching?.join()
                // Read from the store, not the screen's state, which can lag a tap just made.
                if (!container.settings.commitAsNoReply()) setCommitAsNoReply(true)
                switching?.join()
                // Only once the address is kept: a lookup can't go out once the VPN is required
                // and there's none, and a retry would then fail the same way.
                if (container.settings.commitAsNoReply()) setOnlyThroughVpn(true).join()
            } finally {
                local.update { it.copy(settingUpAnonymous = false) }
            }
        }
    }

    fun setSendAtRandomTime(on: Boolean) = viewModelScope.launch { container.settings.setSendAtRandomTime(on) }

    fun setPlainCommitMessages(on: Boolean) = viewModelScope.launch { container.settings.setPlainCommitMessages(on) }

    // In the app's scope: once the switch is on, the connections made without it must close
    // even if the screen is left at once.
    fun setOnlyThroughVpn(on: Boolean) = container.appScope.launch { container.setOnlyThroughVpn(on) }

    /**
     * Sets the site's time zone to [zone]: one commit changing only `_config.yml`'s `timezone:`
     * line, and only if the file is still as read.
     */
    fun useZone(zone: String) {
        if (local.value.settingZone) return
        local.update { it.copy(settingZone = true, zoneBeingSet = zone, message = null) }
        viewModelScope.launch {
            val message = try {
                val blog = container.blogs.blog() ?: error("Sign in to change the blog.")
                val account = container.accounts.current() ?: error("Sign in to change the blog.")
                // Between publishes: one being planned would date its post in the old zone.
                container.publisher.betweenPublishes {
                    val current = blog.file(CONFIG)
                    val author = container.settings.commitAuthor(account.login) { blog.user().noReplyAuthor }
                    blog.commit(
                        // Not naming it: the commit list is read more than _config.yml is.
                        "Set the site's time zone",
                        listOf(FileChange.text(CONFIG, ConfigEdit.withTimezone(current?.text, zone))),
                        mapOf(CONFIG to current?.sha), author,
                    )
                }
                // On its own: the commit has landed even if reading the blog again fails, and
                // saying otherwise would invite committing it twice.
                local.update { it.copy(committedZone = zone, zoneBefore = container.blogs.config.value.timezone?.id) }
                runCatching { container.blogs.refresh() }
                "The site's time zone is now $zone. The site rebuilds in a minute or two."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: GitHubException) {
                if (e.kind == GitHubException.Kind.Changed) "_config.yml changed on GitHub just now. Try again." else e.forWriter()
            } catch (e: Exception) {
                e.message ?: "Couldn't change the time zone."
            }
            local.update { it.copy(settingZone = false, zoneBeingSet = null, message = message) }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private companion object {
        const val CONFIG = "_config.yml"
    }
}
