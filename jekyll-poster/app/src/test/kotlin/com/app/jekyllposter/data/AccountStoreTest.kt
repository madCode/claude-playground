package com.app.jekyllposter.data

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.github.DeviceFlow
import com.app.jekyllposter.testutil.testCipher
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountStoreTest {
    private val account = Account("writer", "github_pat_secret", "writer", "blog", "main", "https://writer.github.io/blog/")
    private val dbName = "accounts-${System.nanoTime()}.db"
    private var db = openDb()
    private val dataFile = File.createTempFile("account", ".preferences_pb").also { it.delete() }
    private val data: DataStore<Preferences> = PreferenceDataStoreFactory.create { dataFile }

    // A file, as in the app: the import's rows must outlast a restart.
    private fun openDb() = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PosterDatabase::class.java, dbName)
        .allowMainThreadQueries().build()

    @After fun close() {
        db.close()
        dataFile.delete()
    }

    private fun store(
        cipher: SecretCipher = testCipher(),
        refresh: (suspend (String, String) -> DeviceFlow.Tokens)? = null,
        now: () -> Long = System::currentTimeMillis,
    ) = AccountStore(data, db.accounts(), cipher, refresh, now)

    private suspend fun signInOf(key: String) = db.accounts().blog(key)!!.signInId

    private fun dbBytes() = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(dbName).readBytes()

    @Test fun theTokenIsSealedOnDiskAndReadBack() = runBlocking {
        val store = store()
        store.save(account)
        assertEquals(account, store.current())
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        assertFalse(String(dbBytes(), Charsets.ISO_8859_1).contains("github_pat_secret"))
        store.remove(signInOf(account.blogKey))
        assertNull(store.current())
    }

    @Test fun anExpiringTokenIsRenewedBeforeUse() = runBlocking {
        var now = 1_000_000L
        val renewals = mutableListOf<String>()
        val store = store(refresh = { _, refresh ->
            renewals += refresh
            DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new")
        }) { now }
        store.save(account.copy(token = "ghu_old", refreshToken = "ghr_old", expiresAt = now + 60 * 60_000))
        assertEquals("ghu_old", store.current()!!.token)
        now += 58 * 60_000
        val renewed = store.current()!!
        assertEquals("ghu_new", renewed.token)
        assertEquals(listOf("ghr_old"), renewals)
        assertEquals(now + 28_800_000, renewed.expiresAt)
        // Kept, sealed like the token.
        assertEquals("ghr_new", store.current()!!.refreshToken)
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        assertFalse(String(dbBytes(), Charsets.ISO_8859_1).contains("ghr_new"))
    }

    @Test fun aBlogThatIsntOpenRenewsItsOwnSignInWhenUsed() = runBlocking {
        var now = 1_000_000L
        val store = store(refresh = { _, _ -> DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new") }) { now }
        store.save(account.copy(token = "ghu_old", refreshToken = "ghr_old", expiresAt = now + 60 * 60_000))
        assertTrue(store.addBlog(signInOf(account.blogKey), "writer", "other", "main", null))
        now += 58 * 60_000
        val left = store.forBlog("writer/blog@main")!!
        assertEquals("ghu_new", left.token)
        assertEquals("writer/blog@main", left.blogKey)
        // Still on the other blog, which shares that sign-in and so its renewal.
        assertEquals("writer/other@main", store.current()!!.blogKey)
        assertEquals("ghu_new", store.current()!!.token)
    }

    @Test fun addingABlogKeepsTheOneLeftAndRemovingTheSignInRemovesBoth() = runBlocking {
        val store = store()
        store.save(account)
        val signIn = signInOf(account.blogKey)
        assertTrue(store.addBlog(signIn, "writer", "other", "main", "https://writer.github.io/other/"))
        assertEquals(account, store.forBlog(account.blogKey))
        assertEquals("https://writer.github.io/other/", store.account.first()!!.siteUrl)
        val removal = store.remove(signIn) as Removal.Removed
        assertEquals(setOf("writer/blog@main", "writer/other@main"), removal.blogs.toSet())
        assertNull(store.current())
        assertNull(store.forBlog(account.blogKey))
        assertFalse(store.addBlog(signIn, "writer", "blog", "main", null))
    }

    @Test fun aSignInIsntRemovedWhileAPostIsStillToGoOutWithIt() = runBlocking {
        val store = store()
        store.save(account)
        val signIn = signInOf(account.blogKey)
        val id = db.drafts().insert(Draft(blog = account.blogKey, title = "At its random time", state = PostState.Queued))
        // A failed post and a draft don't hold it: they stay on the phone, as drafts do.
        db.drafts().insert(Draft(blog = account.blogKey, title = "Didn't go", state = PostState.Failed))
        db.drafts().insert(Draft(blog = account.blogKey, title = "Still writing"))
        val refused = store.remove(signIn) as Removal.Refused
        assertEquals(listOf(id), refused.waiting.map { it.id })
        assertEquals(account, store.current())
        db.drafts().update(db.drafts().get(id)!!.copy(state = PostState.Published))
        assertTrue(store.remove(signIn) is Removal.Removed)
        assertNull(store.current())
        assertEquals(3, db.drafts().list().size)
    }

    @Test fun removingTheOpenBlogsSignInOpensAnotherBlog() = runBlocking {
        val store = store()
        store.save(account)
        val inkwell = account.copy(login = "inkwell", token = "github_pat_inkwell", owner = "inkwell", repo = "night-notes", siteUrl = null)
        store.save(inkwell)
        assertEquals(inkwell.blogKey, store.currentKey())
        assertEquals(
            listOf("writer" to "writer/blog@main", "inkwell" to "inkwell/night-notes@main"),
            store.blogs.first().map { it.login to it.key },
        )
        store.remove(signInOf(inkwell.blogKey))
        assertEquals(account, store.current())
        assertEquals(listOf("writer/blog@main"), store.blogs.first().map { it.key })
    }

    @Test fun removingTheOpenBlogsSignInNeverReadsAsSignedOut() = runBlocking {
        val store = store()
        store.save(account)
        val inkwell = account.copy(login = "inkwell", token = "github_pat_inkwell", owner = "inkwell", repo = "night-notes")
        store.save(inkwell)
        val seen = java.util.concurrent.CopyOnWriteArrayList<Account?>()
        val watching = launch(Dispatchers.IO) { store.account.collect { seen += it } }
        idleUntilTrue { seen.lastOrNull()?.blogKey == inkwell.blogKey }
        store.remove(signInOf(inkwell.blogKey))
        idleUntilTrue { seen.lastOrNull()?.blogKey == account.blogKey }
        watching.cancel()
        // The screens read a null as signed out, and would leave for the sign-in screen.
        assertTrue(seen.toString(), seen.none { it == null })
    }

    private fun idleUntilTrue(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met" }
            Thread.sleep(10)
        }
    }

    @Test fun aBlogKeepsItsTitleWhenItsSignInIsReplaced() = runBlocking {
        val store = store()
        store.save(account)
        store.setTitle(account.blogKey, "A Sample Notebook")
        store.save(account.copy(token = "github_pat_new"))
        assertEquals("A Sample Notebook", store.blogs.first().single().title)
    }

    @Test fun aSignInNoBlogUsesAnyMoreIsRemoved() = runBlocking {
        val store = store()
        store.save(account)
        // A blog with a sign-in of its own, as one moved from another phone has.
        store.save(account.copy(repo = "other", token = "github_pat_other"))
        assertTrue(store.addBlog(signInOf("writer/other@main"), "writer", "blog", "main", null))
        db.query("SELECT COUNT(*) FROM sign_ins", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        assertEquals("github_pat_other", store.forBlog(account.blogKey)!!.token)
    }

    @Test fun aRenewalFinishingAfterRemovalDoesntAddTheSignInBack() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = store(refresh = { _, _ ->
            started.complete(Unit); release.await()
            DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new")
        }) { 0L }
        store.save(account.copy(refreshToken = "ghr_old", expiresAt = 60_000))
        val signIn = signInOf(account.blogKey)
        val renewing = async(Dispatchers.IO) { store.current() }
        started.await()
        val signingOut = async(Dispatchers.IO) { store.remove(signIn) }
        release.complete(Unit)
        renewing.await(); signingOut.await()
        assertNull(store.current())
        assertNull(store.forBlog(account.blogKey))
    }

    @Test fun aRenewalCancelledMidwayStillKeepsTheNewTokens() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = store(refresh = { _, _ ->
            started.complete(Unit); release.await()
            DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new")
        }) { 0L }
        store.save(account.copy(refreshToken = "ghr_old", expiresAt = 60_000))
        // A screen that asked for the account, closed while GitHub was answering.
        val asking = async(Dispatchers.IO) { store.current() }
        started.await()
        asking.cancel()
        release.complete(Unit)
        runCatching { asking.await() }
        // GitHub replaced the refresh token as it answered: the new one is kept. Read as stored,
        // without renewing again, which would hide a dropped answer.
        assertEquals("ghr_new", store.account.first()!!.refreshToken)
    }

    @Test fun aTokenThatCannotBeUnsealedReadsAsSignedOut() = runBlocking {
        store().save(account)
        val otherKey = AesGcmCipher { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
        assertNull(store(otherKey).current())
    }

    /** The account as the app kept it before sign-ins had a table. */
    private suspend fun writeOldAccount() {
        val cipher = testCipher()
        fun seal(s: String) = Base64.encodeToString(cipher.encrypt(s.toByteArray()), Base64.NO_WRAP)
        data.edit {
            it[stringPreferencesKey("login")] = "writer"
            it[stringPreferencesKey("token")] = seal("ghu_old")
            it[stringPreferencesKey("refresh_token")] = seal("ghr_old")
            it[longPreferencesKey("expires_at")] = 4_000_000_000_000L
            it[stringPreferencesKey("owner")] = "writer"
            it[stringPreferencesKey("repo")] = "blog"
            it[stringPreferencesKey("branch")] = "main"
            it[stringPreferencesKey("site_url")] = "https://writer.github.io/blog/"
        }
    }

    private val imported = Account("writer", "ghu_old", "writer", "blog", "main", "https://writer.github.io/blog/", "ghr_old", 4_000_000_000_000L)

    @Test fun anAccountFromBeforeIsMovedIntoTheDatabaseWithItsDrafts() = runBlocking {
        writeOldAccount()
        val unsaved = db.drafts().insert(Draft(title = "No blog yet", state = PostState.Queued))
        val other = db.drafts().insert(Draft(blog = "writer/elsewhere@main", title = "Elsewhere"))
        val store = store()
        assertEquals(imported, store.account.first())
        assertEquals(imported, store.current())
        assertEquals("writer/blog@main", db.drafts().get(unsaved)!!.blog)
        assertEquals("writer/elsewhere@main", db.drafts().get(other)!!.blog)
        // The old record is gone: only the open blog's key is left.
        assertEquals(setOf("current_blog"), data.data.first().asMap().keys.map { it.name }.toSet())
        assertEquals(SignInKind.GitHubApp, db.accounts().signIn(db.accounts().blog("writer/blog@main")!!.signInId)!!.kind)
    }

    @Test fun anImportStoppedBeforeTheOldRecordWasClearedFinishesOnceOnTheNextStart() = runBlocking {
        writeOldAccount()
        assertEquals(imported, store().current())
        // As if the app had been stopped after writing the rows, before clearing the old record.
        data.edit { it.remove(stringPreferencesKey("current_blog")) }
        writeOldAccount()
        db.close()
        db = openDb()
        val restarted = store()
        assertEquals(imported, restarted.current())
        db.query("SELECT (SELECT COUNT(*) FROM sign_ins), (SELECT COUNT(*) FROM blogs)", null).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
            assertEquals(1, c.getInt(1))
        }
        assertEquals(setOf("current_blog"), data.data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test fun nothingToImportIsSignedOut() = runBlocking {
        assertNull(store().current())
        assertNull(store().account.first())
    }
}
