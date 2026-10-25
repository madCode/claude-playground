package com.app.jekyllposter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.jekyllposter.AppContainer
import com.app.jekyllposter.core.github.FileChange
import com.app.jekyllposter.core.github.GitHubException
import com.app.jekyllposter.core.jekyll.ConfigEdit
import com.app.jekyllposter.data.BlogPrivacy
import com.app.jekyllposter.publish.dayInUtc
import com.app.jekyllposter.ui.catching
import com.app.jekyllposter.ui.forWriter
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the blog and the app tell GitHub and readers beyond the posts, and the switches for it. */
enum class Visibility { Loading, Public, Private, Unknown }

class BlogPrivacyViewModel(private val container: AppContainer, private val phoneZone: () -> ZoneId = { ZoneId.systemDefault() }) : ViewModel() {
    data class State(
        /** The blog's own title, which the page is named for, once read. */
        val title: String? = null,
        /** Added beside other blogs, and nothing published to it yet. */
        val isNew: Boolean = false,
        val repoName: String? = null,
        val branch: String? = null,
        /** `<id>+<login>@users.noreply.github.com`, once the account is read. */
        val noReplyEmail: String? = null,
        val commitAsNoReply: Boolean = false,
        val removeTrackingCodes: Boolean = false,
        val sendWebmentions: Boolean = false,
        val showWebmentions: Boolean = false,
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

    /** The open blog's switches; a switch to another blog shows that one's. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val switches = container.accounts.currentKey.flatMapLatest { container.privacy.of(it) }

    val state: StateFlow<State> = combine(
        local, switches, container.waitingForVpn, container.blogs.config, container.accounts.account,
    ) { s, p, waiting, config, account ->
        s.copy(
            commitAsNoReply = p.commitAsNoReply, removeTrackingCodes = p.removeTrackingCodes, onlyThroughVpn = p.onlyThroughVpn,
            datesByDayOnly = p.datesByDayOnly, sendAtRandomTime = p.sendAtRandomTime, plainCommitMessages = p.plainCommitMessages,
            sendWebmentions = p.sendWebmentions, showWebmentions = p.showWebmentions, isNew = p.isNew, waitingForVpn = waiting, title = config.title,
            siteZone = s.committedZone?.takeIf { config.timezone?.id == s.zoneBefore } ?: config.timezone?.id,
            repoName = account?.repoName, branch = account?.branch, login = account?.login,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, local.value)

    /** The open blog, whose switches every change here is to. */
    private suspend fun key(): String? = container.accounts.currentKey()

    init {
        // Read again for each blog opened: the address and visibility are the open blog's.
        viewModelScope.launch {
            container.accounts.currentKey.distinctUntilChanged().collectLatest {
                local.update { s -> s.copy(noReplyEmail = null, visibility = Visibility.Loading) }
                val blog = container.blogs.blog()
                val user = blog?.let { b -> catching { b.user() }.getOrNull() }
                local.update { it.copy(noReplyEmail = user?.noReplyAuthor?.email) }
                val repo = blog?.let { b -> catching { b.repository() }.getOrNull() }
                local.update { it.copy(visibility = repo?.let { r -> if (r.private) Visibility.Private else Visibility.Public } ?: Visibility.Unknown) }
            }
        }
    }

    /**
     * On, the account's no-reply address is looked up now and kept, so publishing needs no extra
     * call; if it can't be, the switch stays off and says why.
     */
    private var switching: Job? = null

    fun setCommitAsNoReply(on: Boolean) = setCommitAsNoReply(on, null)

    /** For blog [key], or the open one; see [setCommitAsNoReply]. */
    private fun setCommitAsNoReply(on: Boolean, key: String?) {
        // The last tap wins: an "on" still looking up the address mustn't land after an "off".
        switching?.cancel()
        switching = viewModelScope.launch {
            val account = (key?.let { container.accounts.forBlog(it) } ?: container.accounts.current()) ?: return@launch
            if (!on) return@launch container.privacy.setCommitAsNoReply(account.blogKey, account.login, null)
            val author = catching { container.blogs.blog(account).user().noReplyAuthor }.getOrNull()
            ensureActive()
            if (author == null) {
                // The VPN switch on and no VPN: say so, or "try again" would fail the same way.
                val why = if (container.waitingForVpn(account.blogKey).first()) "Connect your VPN, then try again: nothing goes to GitHub without it."
                else "Try again."
                local.update { it.copy(message = "Couldn't find your GitHub no-reply address just now. $why") }
                return@launch
            }
            container.privacy.setCommitAsNoReply(account.blogKey, account.login, author)
            // Shown only on its own blog's page: another may have been opened meanwhile.
            if (key() == account.blogKey) local.update { it.copy(noReplyEmail = author.email) }
        }
    }

    private fun set(change: (BlogPrivacy) -> BlogPrivacy) = viewModelScope.launch { key()?.let { container.privacy.update(it, change) } }

    fun setRemoveTrackingCodes(on: Boolean) = set { it.copy(removeTrackingCodes = on) }

    fun setDatesByDayOnly(on: Boolean) = set { it.copy(datesByDayOnly = on) }

    fun setSendWebmentions(on: Boolean) = set { it.copy(sendWebmentions = on) }

    fun setShowWebmentions(on: Boolean) = set { it.copy(showWebmentions = on) }

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
                val key = key() ?: return@launch
                container.privacy.update(key) {
                    it.copy(plainCommitMessages = true, sendAtRandomTime = true, datesByDayOnly = true, removeTrackingCodes = true)
                }
                switching?.join()
                // Read from the store, not the screen's state, which can lag a tap just made.
                // The blog it started on throughout, even if another is opened meanwhile.
                if (!container.privacy.get(key).commitAsNoReply) setCommitAsNoReply(true, key)
                switching?.join()
                // Only once the address is kept: a lookup can't go out once the VPN is required
                // and there's none, and a retry would then fail the same way.
                if (container.privacy.get(key).commitAsNoReply) container.appScope.launch { container.setOnlyThroughVpn(key, true) }.join()
            } finally {
                local.update { it.copy(settingUpAnonymous = false) }
            }
        }
    }

    fun setSendAtRandomTime(on: Boolean) = set { it.copy(sendAtRandomTime = on) }

    fun setPlainCommitMessages(on: Boolean) = set { it.copy(plainCommitMessages = on) }

    // In the app's scope: once the switch is on, the connections made without it must close
    // even if the screen is left at once.
    fun setOnlyThroughVpn(on: Boolean) = container.appScope.launch { key()?.let { container.setOnlyThroughVpn(it, on) } }

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
                    val author = container.privacy.commitAuthor(account.blogKey, account.login) { blog.user().noReplyAuthor }
                    // Dated by the day alone, as a post's commit is; see Publisher.
                    val date = if (container.privacy.get(account.blogKey).datesByDayOnly && author != null) dayInUtc(LocalDate.now(ZoneOffset.UTC)) else null
                    blog.commit(
                        // Not naming it: the commit list is read more than _config.yml is.
                        "Set the site's time zone",
                        listOf(FileChange.text(CONFIG, ConfigEdit.withTimezone(current?.text, zone))),
                        mapOf(CONFIG to current?.sha), author, date,
                    )
                }
                // On its own: the commit has landed even if reading the blog again fails, and
                // saying otherwise would invite committing it twice.
                local.update { it.copy(committedZone = zone, zoneBefore = container.blogs.config.value.timezone?.id) }
                catching { container.blogs.refresh() }
                "The site's time zone is now $zone. The site rebuilds in a minute or two."
            } catch (e: CancellationException) {
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
