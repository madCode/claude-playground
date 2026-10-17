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
        refresh: (suspend (String) -> DeviceFlow.Tokens)? = null,
        now: () -> Long = System::currentTimeMillis,
    ) = AccountStore(data, db.accounts(), cipher, refresh, now)

    private fun dbBytes() = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(dbName).readBytes()

    @Test fun theTokenIsSealedOnDiskAndReadBack() = runBlocking {
        val store = store()
        store.save(account)
        assertEquals(account, store.current())
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        assertFalse(String(dbBytes(), Charsets.ISO_8859_1).contains("github_pat_secret"))
        store.signOut()
        assertNull(store.current())
    }

    @Test fun anExpiringTokenIsRenewedBeforeUse() = runBlocking {
        var now = 1_000_000L
        val renewals = mutableListOf<String>()
        val store = store(refresh = { refresh ->
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
        val store = store(refresh = { _ -> DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new") }) { now }
        store.save(account.copy(token = "ghu_old", refreshToken = "ghr_old", expiresAt = now + 60 * 60_000))
        assertTrue(store.switchTo("writer", "other", "main", null))
        now += 58 * 60_000
        val left = store.forBlog("writer/blog@main")!!
        assertEquals("ghu_new", left.token)
        assertEquals("writer/blog@main", left.blogKey)
        // Still on the other blog, which shares that sign-in and so its renewal.
        assertEquals("writer/other@main", store.current()!!.blogKey)
        assertEquals("ghu_new", store.current()!!.token)
    }

    @Test fun switchingKeepsTheBlogLeftAndSigningOutRemovesBoth() = runBlocking {
        val store = store()
        store.save(account)
        assertTrue(store.switchTo("writer", "other", "main", "https://writer.github.io/other/"))
        assertEquals(account, store.forBlog(account.blogKey))
        assertEquals("https://writer.github.io/other/", store.account.first()!!.siteUrl)
        assertEquals(setOf("writer/blog@main", "writer/other@main"), store.signOut().toSet())
        assertNull(store.current())
        assertNull(store.forBlog(account.blogKey))
        assertFalse(store.switchTo("writer", "blog", "main", null))
    }

    @Test fun aSignInNoBlogUsesAnyMoreIsRemoved() = runBlocking {
        val store = store()
        store.save(account)
        // A blog with a sign-in of its own, as one moved from another phone has.
        store.save(account.copy(repo = "other", token = "github_pat_other"))
        assertTrue(store.switchTo("writer", "blog", "main", null))
        db.query("SELECT COUNT(*) FROM sign_ins", null).use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        assertEquals("github_pat_other", store.forBlog(account.blogKey)!!.token)
    }

    @Test fun aRenewalFinishingAfterSignOutDoesntSignBackIn() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = store(refresh = { _ ->
            started.complete(Unit); release.await()
            DeviceFlow.Tokens("ghu_new", 28_800, "ghr_new")
        }) { 0L }
        store.save(account.copy(refreshToken = "ghr_old", expiresAt = 60_000))
        val renewing = async(Dispatchers.IO) { store.current() }
        started.await()
        val signingOut = async(Dispatchers.IO) { store.signOut() }
        release.complete(Unit)
        renewing.await(); signingOut.await()
        assertNull(store.current())
        assertNull(store.forBlog(account.blogKey))
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
