package com.app.jekyllposter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.core.github.CommitAuthor
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The switches kept for the whole app before they were per blog move to every blog, once. */
@RunWith(AndroidJUnit4::class)
class PrivacyTest {
    private val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PosterDatabase::class.java, "privacy-${System.nanoTime()}.db")
        .allowMainThreadQueries().build()
    private val file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
    private val old: DataStore<Preferences> = PreferenceDataStoreFactory.create { file }

    @After fun close() {
        db.close()
        file.delete()
    }

    private fun privacy() = Privacy(db.privacy(), old) {}

    private suspend fun twoBlogs() {
        val id = db.accounts().insert(SignIn(login = "writer", kind = SignInKind.Token, sealedToken = "sealed"))
        db.accounts().upsert(SavedBlog("writer/blog@main", "writer", "blog", "main", signInId = id))
        db.accounts().upsert(SavedBlog("writer/other@main", "writer", "other", "main", signInId = id))
    }

    private suspend fun oldSwitches() = old.edit {
        it[booleanPreferencesKey("only_through_vpn")] = true
        it[booleanPreferencesKey("commit_as_no_reply")] = true
        it[stringPreferencesKey("no_reply_login")] = "writer"
        it[stringPreferencesKey("no_reply_email")] = "1+writer@users.noreply.github.com"
        it[booleanPreferencesKey("dates_by_day_only")] = true
        // Kept for every blog: the vault folder is the phone's.
        it[stringPreferencesKey("obsidian_vault")] = "content://vault"
    }

    @Test fun everyBlogGetsTheSwitchesKeptBeforeAndTheyAreClearedAfter() = runBlocking {
        twoBlogs()
        oldSwitches()
        val p = privacy()
        for (key in listOf("writer/blog@main", "writer/other@main")) {
            val got = p.get(key)
            assertTrue(got.onlyThroughVpn && got.commitAsNoReply && got.datesByDayOnly)
            assertFalse(got.sendAtRandomTime || got.plainCommitMessages || got.removeTrackingCodes || got.isNew)
            assertEquals(CommitAuthor("writer", "1+writer@users.noreply.github.com"), p.commitAuthor(key, "writer") { null })
        }
        val left = old.data.first()
        assertEquals(setOf("obsidian_vault"), left.asMap().keys.map { it.name }.toSet())
    }

    @Test fun anImportStoppedBeforeTheOldSwitchesWereClearedFinishesTheSame() = runBlocking {
        twoBlogs()
        oldSwitches()
        // As if stopped after the rows were written: they're there, and so are the old switches.
        db.privacy().giveEveryBlog(BlogPrivacy("", onlyThroughVpn = true, commitAsNoReply = true, datesByDayOnly = true))
        privacy().get("writer/blog@main")
        assertEquals(2, db.privacy().list().size)
        assertTrue(db.privacy().list().all { it.onlyThroughVpn && it.datesByDayOnly })
        assertFalse(booleanPreferencesKey("only_through_vpn") in old.data.first())
    }

    @Test fun aChangeOnOneBlogIsntSeenOnAnother() = runBlocking {
        twoBlogs()
        val p = privacy()
        p.update("writer/blog@main") { it.copy(removeTrackingCodes = true) }
        assertTrue(p.get("writer/blog@main").removeTrackingCodes)
        assertFalse(p.get("writer/other@main").removeTrackingCodes)
        assertFalse(p.anyOnlyThroughVpn())
        p.update("writer/other@main") { it.copy(onlyThroughVpn = true) }
        assertTrue(p.anyOnlyThroughVpn())
    }
}
