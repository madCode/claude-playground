package com.app.jekyllposter.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Typing `[[` offers the blog's posts; picking one writes `[[its title]]`, linked when published. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class LinkSuggestionsTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container

    @After fun close() = app.github.close()

    private fun editor(): EditorViewModel {
        runBlocking {
            c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
            c.blogs.refresh()
        }
        val id = runBlocking { c.drafts.insert(Draft(title = "More reading")) }
        return EditorViewModel(c, id).also { e -> idleUntil { e.text != null && e.postsToLink("").isNotEmpty() } }
    }

    @Test fun typingALinkOffersPostsAndPickingOneWritesItsTitle() {
        val editor = editor()
        editor.setBody(TextFieldValue("After [[read", TextRange(12)))
        assertEquals("read", editor.openLink!!.query)
        assertEquals(listOf("What I read in April"), editor.postsToLink("read").map { it.title })
        editor.linkTo(editor.postsToLink("read").single())
        assertEquals("After [[What I read in April]]", editor.text!!.body)
        assertEquals(TextRange(30), editor.bodySelection)
        // Closed now: nothing more to offer.
        assertNull(editor.openLink)
    }

    @Test fun thePreviewShowsTheLinkAsItWillBePublished() {
        val editor = editor()
        editor.setBody("See [[What I read in April]].")
        val html = runBlocking { editor.previewHtml(dark = false) }
        assertTrue(html, html.contains("<a href=\"#\">What I read in April</a>"))
    }

    @Test fun publishingLinksTheTitlesThenSoEveryRetrySendsTheSameText() {
        val editor = editor()
        editor.setBody("After [[What I read in April]] and [[Not yet]].")
        editor.publish()
        idleUntil(10_000) { editor.state.value.closed }
        val sent = runBlocking { c.drafts.list() }.single()
        assertEquals("After [What I read in April]({{ site.baseurl }}{% post_url 2025-04-20-reading-list %}) and [[Not yet]].", sent.body)
    }

    @Test fun theToolbarButtonStartsALinkWithTheSelectionAsTheSearch() {
        val editor = editor()
        editor.setBody(TextFieldValue("As in April", TextRange(6, 11)))
        editor.format(com.app.jekyllposter.core.jekyll.MarkdownEdits::postLink)
        assertEquals("April", editor.openLink!!.query)
        assertEquals(listOf("What I read in April"), editor.postsToLink("April").map { it.title })
    }
}
