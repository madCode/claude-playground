package com.app.jekyllposter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.app.jekyllposter.core.github.CommitAuthor
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One blog's privacy switches. Each is off by default, so the app does what GitHub and Jekyll
 * would on their own and never changes what was written; turning one on tightens things. A blog
 * with no row has every switch off.
 */
@Entity(tableName = "blog_privacy")
data class BlogPrivacy(
    @PrimaryKey val blogKey: String,
    /** Nothing for this blog goes to GitHub, or to its site, except through a VPN. */
    val onlyThroughVpn: Boolean = false,
    /** Commits name the account's no-reply address, whatever its GitHub email settings. */
    val commitAsNoReply: Boolean = false,
    /** The address [commitAsNoReply] uses, kept so publishing needs no extra call, and whose it is. */
    val noReplyLogin: String? = null,
    val noReplyEmail: String? = null,
    /** Posts go out at a random moment in the [RANDOM_WINDOW] after Publish, not at once. */
    val sendAtRandomTime: Boolean = false,
    /** Commits say "Update blog", not the post's title, which stays in the history. */
    val plainCommitMessages: Boolean = false,
    /** New posts are dated by the day alone: no time of day, no time zone. */
    val datesByDayOnly: Boolean = false,
    /** Links shared into the app lose their tracking codes, in the editor where the writer sees it. */
    val removeTrackingCodes: Boolean = false,
    /** Added beside other blogs, and nothing published to it yet: Blog & privacy says to set these. */
    val isNew: Boolean = false,
) {
    /** Every switch for writing anonymously is on. */
    val anonymous: Boolean
        get() = onlyThroughVpn && commitAsNoReply && plainCommitMessages && sendAtRandomTime && datesByDayOnly && removeTrackingCodes
}

@Dao
abstract class PrivacyDao {
    @Query("SELECT * FROM blog_privacy WHERE blogKey = :key")
    abstract fun watch(key: String): Flow<BlogPrivacy?>

    @Query("SELECT * FROM blog_privacy WHERE blogKey = :key")
    abstract suspend fun get(key: String): BlogPrivacy?

    @Query("SELECT * FROM blog_privacy")
    abstract fun all(): Flow<List<BlogPrivacy>>

    @Query("SELECT * FROM blog_privacy")
    abstract suspend fun list(): List<BlogPrivacy>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(privacy: BlogPrivacy)

    @Query("SELECT blogKey FROM blogs")
    abstract suspend fun blogKeys(): List<String>

    /** [privacy] given to every blog, for the switches kept before they were per blog. */
    @Transaction
    open suspend fun giveEveryBlog(privacy: BlogPrivacy) {
        blogKeys().forEach { upsert(privacy.copy(blogKey = it)) }
    }
}

/**
 * The privacy switches of each blog, by its key ([Account.blogKey]). The Obsidian vault folder,
 * which is on the phone and not any blog's, stays in [Settings].
 */
class Privacy(
    private val dao: PrivacyDao,
    /** Where the switches were kept, for every blog at once, before they were per blog. */
    private val old: DataStore<Preferences>,
    /** Waits until the account kept before sign-ins had a table is in the blogs table. */
    private val blogsReady: suspend () -> Unit,
) {
    fun of(key: String?): Flow<BlogPrivacy> = flow {
        imported()
        emitAll(if (key == null) kotlinx.coroutines.flow.flowOf(null) else dao.watch(key))
    }.map { it ?: BlogPrivacy(key.orEmpty()) }

    suspend fun get(key: String?): BlogPrivacy {
        imported()
        return key?.let { dao.get(it) } ?: BlogPrivacy(key.orEmpty())
    }

    /** Every blog's that has a row. */
    val all: Flow<List<BlogPrivacy>> = flow {
        imported()
        emitAll(dao.all())
    }

    /** Whether any blog asks for the VPN: then signing in and adding a blog, for no blog yet, does too. */
    suspend fun anyOnlyThroughVpn(): Boolean {
        imported()
        return dao.list().any { it.onlyThroughVpn }
    }

    private val lock = Mutex()

    /** Changes blog [key]'s switches; one at a time, so two quick taps can't undo each other. */
    suspend fun update(key: String, change: (BlogPrivacy) -> BlogPrivacy) {
        imported()
        lock.withLock { dao.upsert(change(dao.get(key) ?: BlogPrivacy(key)).copy(blogKey = key)) }
    }

    /**
     * Turns the no-reply author on for blog [key] with [author], the address of the account
     * [login]; or off, with null. The address stays when off.
     */
    suspend fun setCommitAsNoReply(key: String, login: String, author: CommitAuthor?) = update(key) {
        if (author == null) it.copy(commitAsNoReply = false) else it.copy(commitAsNoReply = true, noReplyLogin = login, noReplyEmail = author.email)
    }

    /**
     * Who [login]'s commits to blog [key] are by: null for GitHub's default. When the switch is on
     * but the kept address is another account's (the blog's sign-in changed since), [lookUp] finds
     * this one's, which is kept without touching the switch: the writer may have turned it off
     * meanwhile. Named by the login, never the profile's name.
     */
    suspend fun commitAuthor(key: String, login: String, lookUp: suspend () -> CommitAuthor?): CommitAuthor? {
        val p = get(key)
        if (!p.commitAsNoReply) return null
        if (p.noReplyLogin == login && p.noReplyEmail != null) return CommitAuthor(login, p.noReplyEmail)
        val found = lookUp() ?: throw NoAddress(login)
        update(key) { it.copy(noReplyLogin = login, noReplyEmail = found.email) }
        return CommitAuthor(login, found.email)
    }

    /** Blog [key] was added beside others: its Blog & privacy says to set the switches first. */
    suspend fun markNew(key: String) = update(key) { it.copy(isNew = true) }

    /** GitHub gave no account id to build [login]'s no-reply address from. */
    class NoAddress(login: String) : Exception("No no-reply address for $login")

    private val importLock = Mutex()

    @Volatile private var importDone = false

    /**
     * Gives every blog the switches kept for the whole app before they were per blog, once. The
     * rows are written first and the old keys cleared after: stopped in between, the next start
     * writes the same rows again and then clears them.
     */
    private suspend fun imported() {
        if (importDone) return
        importLock.withLock {
            if (importDone) return
            val prefs = old.data.first()
            if (OLD_KEYS.any { it in prefs }) {
                blogsReady()
                dao.giveEveryBlog(
                    BlogPrivacy(
                        blogKey = "",
                        onlyThroughVpn = prefs[ONLY_THROUGH_VPN] ?: false,
                        commitAsNoReply = prefs[NO_REPLY] ?: false,
                        noReplyLogin = prefs[NO_REPLY_LOGIN],
                        noReplyEmail = prefs[NO_REPLY_EMAIL],
                        sendAtRandomTime = prefs[RANDOM_TIME] ?: false,
                        plainCommitMessages = prefs[PLAIN_MESSAGES] ?: false,
                        datesByDayOnly = prefs[DAY_ONLY] ?: false,
                        removeTrackingCodes = prefs[REMOVE_TRACKING] ?: false,
                    ),
                )
                old.edit { p -> OLD_KEYS.forEach { p.remove(it) } }
            }
            importDone = true
        }
    }

    private companion object {
        // The switches as kept for the whole app before they were per blog; read only to move them.
        val REMOVE_TRACKING = booleanPreferencesKey("remove_tracking_codes")
        val NO_REPLY = booleanPreferencesKey("commit_as_no_reply")
        val NO_REPLY_LOGIN = stringPreferencesKey("no_reply_login")
        val NO_REPLY_EMAIL = stringPreferencesKey("no_reply_email")
        val RANDOM_TIME = booleanPreferencesKey("send_at_random_time")
        val PLAIN_MESSAGES = booleanPreferencesKey("plain_commit_messages")
        val DAY_ONLY = booleanPreferencesKey("dates_by_day_only")
        val ONLY_THROUGH_VPN = booleanPreferencesKey("only_through_vpn")

        // Kept by older versions, and maybe a real name: not needed any more.
        val NO_REPLY_NAME = stringPreferencesKey("no_reply_name")
        val OLD_KEYS = listOf(REMOVE_TRACKING, NO_REPLY, NO_REPLY_LOGIN, NO_REPLY_EMAIL, RANDOM_TIME, PLAIN_MESSAGES, DAY_ONLY, ONLY_THROUGH_VPN, NO_REPLY_NAME)
    }
}

/** How long after Publish a post sent at a random time can go out. */
val RANDOM_WINDOW: Duration = Duration.parse("3h")
