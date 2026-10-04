package com.app.jekyllposter.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Destination
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.editor.EditorViewModel.TermKind
import com.app.jekyllposter.ui.home.HomeViewModel
import kotlinx.coroutines.launch
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

    @Test fun editingFromAStaleListStartsFromTheBlogAsItIsNow() {
        // Changed on a laptop after the phone last read the blog.
        val path = "_posts/2025-01-12-welcome.md"
        app.github.push("Laptop", mapOf(path to "---\ntitle: Welcome back\ncategories: [meta]\n---\n\nFrom the laptop.\n"))
        val home = HomeViewModel(c)
        val stale = runBlocking { c.database.posts().snapshot() }.first { it.path == path }
        var id: Long? = null
        home.edit(stale) { id = it }
        idleUntil { id != null }
        val editor = EditorViewModel(c, id!!)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        assertEquals("Welcome back", editor.text!!.title)
        editor.setBody("From the laptop. And the phone.")
        editor.publish()
        idleUntil { app.github.text(path)!!.contains("And the phone.") }
    }

    @Test fun deletingAPostFromTheBlog() {
        val home = HomeViewModel(c)
        var id: Long? = null
        val post = runBlocking { c.database.posts().snapshot() }.first { it.path == "_posts/2025-04-20-reading-list.md" }
        home.edit(post) { id = it }
        idleUntil { id != null }
        val editor = EditorViewModel(c, id!!)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        editor.deleteFromBlog()
        idleUntil { app.github.text(post.path) == null }
        idleUntil { runBlocking { c.drafts.get(id!!) } == null }
        assertTrue(editor.state.value.closed)
    }

    @Test fun aDeleteSentAgainKeepsItsMarkerAndAnUpdateDropsIt() {
        val path = "_posts/2025-04-20-reading-list.md"
        // A delete whose commit may have landed, then failed on sign-in before it could tell.
        val id = runBlocking {
            c.drafts.insert(Draft(title = "Reading", editingPath = path, baseSha = "x", destination = Destination.Delete, targetPath = path, state = PostState.Failed))
        }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        app.github.push("Deleted by the lost commit", mapOf(path to null))
        editor.deleteFromBlog()
        // Recognised as this phone's delete, not "moved elsewhere".
        idleUntil { runBlocking { c.drafts.get(id) } == null }

        // A failed delete, sent as an update instead.
        val again = runBlocking {
            c.drafts.insert(Draft(title = "Coastal", body = "x", editingPath = "travel/_posts/2025-06-08-coastal-walk.md", baseSha = "x", destination = Destination.Delete, targetPath = "travel/_posts/2025-06-08-coastal-walk.md", state = PostState.Failed))
        }
        val second = EditorViewModel(c, again)
        idleUntil { second.text != null && second.state.value.draft != null }
        // Sent as an update instead: the delete's marker isn't carried into it. (The stale base
        // sha makes the update fail, which leaves the row to inspect.)
        second.publish(Destination.Posts)
        idleUntil { runBlocking { c.drafts.get(again) }!!.let { it.state == PostState.Failed && it.destination == Destination.Posts } }
        assertEquals(null, runBlocking { c.drafts.get(again) }!!.targetPath)
    }

    @Test fun aNewPostCantBeDeletedFromTheBlog() {
        val id = runBlocking { c.drafts.insert(Draft(title = "Only here", body = "Not on the blog.")) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        editor.deleteFromBlog()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(PostState.Draft, runBlocking { c.drafts.get(id) }!!.state)
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

    @Test fun previewLoadsSiteImagesFromTheLiveSite() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main", siteUrl = "https://sample.github.io/sample-blog/"))
        val id = c.drafts.insert(Draft(title = "Loaf", body = "![Loaf]({{ '/assets/images/2025/loaf.jpg' | relative_url }})"))
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null }
        val html = editor.previewHtml(dark = false)
        assertTrue(html, html.contains("src=\"https://sample.github.io/sample-blog/assets/images/2025/loaf.jpg\""))
        assertTrue(html.contains("<h1>Loaf</h1>"))
    }

    @Test fun theToolbarFormatsAtTheSelectionAndTheTextIsSaved() {
        val id = runBlocking { c.drafts.insert(Draft(title = "T", body = "make this bold")) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        editor.setBody(androidx.compose.ui.text.input.TextFieldValue("make this bold", androidx.compose.ui.text.TextRange(10, 14)))
        editor.format { com.app.jekyllposter.core.jekyll.MarkdownEdits.wrap(it, "**") }
        assertEquals("make this **bold**", editor.text!!.body)
        assertEquals(androidx.compose.ui.text.TextRange(12, 16), editor.bodySelection)
        editor.close()
        idleUntil { editor.state.value.closed }
        assertEquals("make this **bold**", runBlocking { c.drafts.get(id) }!!.body)
    }

    @Test fun forgettingABlogForgetsItsTitleAndAddressToo() = runBlocking {
        assertEquals("A Sample Notebook", c.blogs.config.value.title)
        c.blogs.clear()
        assertEquals(null, c.blogs.config.value.title)
        assertEquals(null, c.blogs.siteUrl.value)
    }

    @Test fun anEditShowsThePostsOtherKeysAndBadYamlStopsPublishing() {
        val home = HomeViewModel(c)
        var id: Long? = null
        val post = runBlocking { c.database.posts().snapshot() }.first { it.path == "_posts/2025-04-20-reading-list.md" }
        home.edit(post) { id = it }
        idleUntil { id != null }
        val editor = EditorViewModel(c, id!!)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        assertEquals("# Kept by hand: the theme reads this for the post card.\nimage: /assets/img/books.png", editor.text!!.extraFrontMatter)
        editor.setExtraFrontMatter("image: [broken")
        assertTrue(editor.extraProblem!!.contains("isn't YAML"))
        editor.publish()
        // Publishing says why it stopped, rather than doing nothing.
        idleUntil { editor.state.value.frontMatterBlocked != null }
        assertTrue(editor.state.value.frontMatterBlocked!!.contains("isn't YAML"))
        assertEquals(PostState.Draft, runBlocking { c.drafts.get(id!!) }!!.state)
        assertTrue(app.published.isEmpty())
    }

    @Test fun anEditOpenedBeforeFrontMatterWasKeptCantOverwriteIt() {
        // As a draft from before the upgrade: editing a post, its other keys never loaded.
        val id = runBlocking { c.drafts.insert(Draft(title = "T", body = "b", editingPath = "_posts/2025-04-20-reading-list.md", baseSha = "x")) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        editor.setExtraFrontMatter("image: /new.png")
        assertEquals(null, editor.text!!.extraFrontMatter)
    }

    @Test fun aPostsOwnOddFrontMatterDoesntBlockAnEditThatLeavesItAlone() {
        val odd = "og_title: *t"
        val id = runBlocking { c.drafts.insert(Draft(title = "T", body = "b", editingPath = "_posts/x.md", baseSha = "x", extraFrontMatter = odd, extraFrontMatterOpened = odd)) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null && editor.state.value.draft != null }
        assertEquals(null, editor.extraProblem)
        editor.setExtraFrontMatter("og_title: *t\nimage: x")
        assertTrue(editor.extraProblem != null)
    }

    @Test fun aDraftWithOnlyFrontMatterIsKeptOnClose() {
        val id = runBlocking { c.drafts.insert(Draft(extraFrontMatter = "image: /cover.jpg")) }
        val editor = EditorViewModel(c, id)
        idleUntil { editor.text != null }
        editor.close()
        idleUntil { editor.state.value.closed }
        assertTrue(runBlocking { c.drafts.get(id) } != null)
    }

    @Test fun aChosenCategoryThatDisappearsShowsAllPostsAgain() {
        val home = HomeViewModel(c)
        // The list only computes while something watches it, as the screen does.
        val watching = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { home.state.collect {} }
        idleUntil(15_000) { home.state.value.onBlog.size == 6 && !home.state.value.refreshing }
        home.filter("travel")
        idleUntil(15_000) { home.state.value.onBlog.size == 1 }
        app.github.push("Rename", mapOf("travel/_posts/2025-06-08-coastal-walk.md" to null, "_posts/2025-06-08-coastal-walk.md" to "---\ntitle: A coastal walk\ncategories: [walks]\n---\n"))
        home.refresh()
        idleUntil(15_000) { home.state.value.onBlog.size == 6 && home.state.value.category == null }
        watching.cancel()
    }

    @Test fun searchMatchesTitlesCategoriesAndTagsWithinTheFilter() {
        runBlocking { c.drafts.insert(Draft(title = "Something else", state = PostState.Failed, error = "GitHub said no.")) }
        val home = HomeViewModel(c)
        val watching = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { home.state.collect {} }
        idleUntil(15_000) { home.state.value.onBlog.size == 6 && !home.state.value.refreshing }
        home.startSearch()
        // Opening the search alone hides nothing.
        idleUntil { home.state.value.query == "" }
        assertEquals(6, home.state.value.onBlog.size)
        home.search("SOURDOUGH")
        idleUntil { home.state.value.onBlog.map { it.title } == listOf("Sourdough, again: a 72% loaf") }
        // The phone's posts aren't narrowed: a failed one stays in sight.
        assertEquals(listOf("Something else"), home.state.value.onPhone.map { it.title })
        // A tag (walking) and a category (Writing) match too.
        home.search("walk")
        idleUntil { home.state.value.onBlog.map { it.title } == listOf("A coastal walk") }
        home.search("writ")
        idleUntil { home.state.value.onBlog.size == 3 }
        // Within the chosen category.
        home.filter("meta")
        idleUntil { home.state.value.onBlog.map { it.title } == listOf("Welcome to the notebook") }
        home.filter("meta")
        idleUntil { home.state.value.onBlog.size == 3 }
        home.stopSearch()
        idleUntil { home.state.value.query == null && home.state.value.onBlog.size == 6 }
        watching.cancel()
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
