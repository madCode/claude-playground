package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.editor.EditorViewModel.TermKind
import com.app.jekyllposter.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class EditorViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @Before fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
        c.blogs.refresh()
        Unit
    }

    @After fun close() = app.github.close()

    @Test fun editingAPostFromTheListAndUpdatingIt() {
        val home = HomeViewModel(c)
        var id: Long? = null
        val post = runBlocking { c.database.posts().snapshot() }.first { it.path == "_posts/2025-04-20-reading-list.md" }
        home.edit(post) { id = it }
        idleUntil { id != null }
        val editor = EditorViewModel(c, id!!)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        editor.setBody("A shorter list.")
        editor.add(TermKind.Tag, "#essays")
        editor.publish()
        idleUntil { app.github.text(post.path)!!.contains("A shorter list.") }
        val text = app.github.text(post.path)!!
        assertTrue(text, text.contains("category: Writing\ntags: [books, essays]\n# Kept by hand"))
        assertEquals(PostState.Published, runBlocking { c.drafts.get(id!!) }!!.state)
    }

    @Test fun suggestionsLeaveOutWhatThePostHasAndMatchLoosely() {
        val id = runBlocking { c.drafts.insert(Draft(categories = listOf("Writing"))) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.taxonomy.categories.isNotEmpty() }
        assertEquals(listOf("bread", "cooking", "meta", "travel"), editor.suggestions(TermKind.Category, "").map { it.name })
        assertEquals(listOf("cooking"), editor.suggestions(TermKind.Category, "COOK").map { it.name })
        // Adding an existing category in another case doesn't add it twice.
        editor.add(TermKind.Category, "writing")
        assertEquals(listOf("Writing"), editor.text!!.categories)
    }

    @Test fun anEmptyDraftIsDroppedOnClose() {
        val id = runBlocking { c.drafts.insert(Draft()) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null }
        editor.close()
        idleUntil { editor.state.value.closed }
        assertEquals(null, runBlocking { c.drafts.get(id) })
    }
}
