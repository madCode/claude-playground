package com.app.jekyllposter.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.BuildState
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.connect.ConnectScreen
import com.app.jekyllposter.ui.connect.ConnectViewModel
import com.app.jekyllposter.ui.editor.EditorScreen
import com.app.jekyllposter.ui.editor.EditorViewModel
import com.app.jekyllposter.ui.home.HomeScreen
import com.app.jekyllposter.ui.home.HomeViewModel
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

/**
 * Renders each screen with real graphics against the sample blog. It catches screens that crash
 * when drawn with realistic data, and leaves PNGs in build/screenshots for reviewing layout.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val out = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    @After fun close() = app.github.close()

    private fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
        c.blogs.refresh()
    }

    private fun shoot(name: String, ready: () -> Boolean = { true }, act: () -> Unit = {}, content: @Composable () -> Unit) {
        compose.setContent { PosterTheme(content = content) }
        idleUntil(condition = ready)
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        File(out, "$name.png").outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * The screen with any open sheet or menu drawn over it. They are windows of their own, and
     * Robolectric captures another window's root as the screen's pixels, so each is drawn from its view.
     */
    private fun capture(): Bitmap {
        val roots = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
        if (roots.size == 1) return compose.onRoot().captureToImage().asAndroidBitmap()
        val screen = roots.first()
        val bitmap = compose.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bitmap)
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val windows = ReflectionHelpers.getField<List<View>>(global, "mViews")
        roots.drop(1).zip(windows.takeLast(roots.size - 1)).forEach { (root, view) ->
            canvas.save()
            canvas.translate(root.positionOnScreen.x - screen.positionOnScreen.x, root.positionOnScreen.y - screen.positionOnScreen.y)
            view.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    @Test fun connect() {
        val vm = ConnectViewModel(c)
        shoot("01-connect") { ConnectScreen(vm) {} }
    }

    @Test fun chooseBlog() {
        val vm = ConnectViewModel(c).apply { setToken("good-token"); checkToken() }
        shoot("02-choose-blog", ready = { vm.state.value.repos != null }) { ConnectScreen(vm) {} }
    }

    @Test fun home() {
        signIn()
        runBlocking {
            c.drafts.insert(Draft(title = "Notes on the new bike lane", body = "Half written.", categories = listOf("cycling")))
            c.drafts.insert(Draft(title = "Sourdough, round four", state = PostState.Published, buildState = BuildState.Building))
            c.drafts.insert(Draft(title = "Weekend links", state = PostState.Failed, error = "Couldn't reach GitHub. Check your connection."))
        }
        val vm = HomeViewModel(c)
        shoot("03-home", ready = { vm.state.value.onBlog.isNotEmpty() && !vm.state.value.refreshing }) {
            HomeScreen(vm, onOpenDraft = {}, onSettings = {})
        }
    }

    private fun editorWithDraft(): EditorViewModel {
        signIn()
        val id = runBlocking {
            c.drafts.insert(
                Draft(
                    title = "A walk to the lighthouse",
                    body = "Out past the harbour wall at low tide, the path runs along the rocks for a mile.\n\n" +
                        "![The lighthouse](/assets/images/2025/lighthouse.jpg)\n\nWe turned back at the second stile.",
                    categories = listOf("travel"),
                    tags = listOf("walking", "weekend"),
                ),
            )
        }
        return EditorViewModel(c, id).also { vm -> idleUntil { vm.text != null && vm.state.value.taxonomy.categories.isNotEmpty() } }
    }

    @Test fun editor() {
        val vm = editorWithDraft()
        shoot("04-editor") { EditorScreen(vm) {} }
    }

    @Test fun publishedPost() {
        signIn()
        val id = runBlocking {
            c.drafts.insert(
                Draft(
                    title = "A walk to the lighthouse", body = "Out past the harbour wall at low tide.", categories = listOf("travel"),
                    state = PostState.Published, buildState = BuildState.Live,
                    postUrl = "https://sample.github.io/sample-blog/travel/2026/10/04/a-walk-to-the-lighthouse/",
                ),
            )
        }
        val vm = EditorViewModel(c, id)
        shoot("06-published", ready = { vm.text != null && vm.state.value.draft != null }) { EditorScreen(vm) {} }
    }

    @Test fun settings() {
        signIn()
        shoot("07-settings", ready = { c.blogs.siteUrl.value != null }) { com.app.jekyllposter.ui.settings.SettingsScreen(c, {}, {}, {}) }
    }

    @Test fun frontMatter() {
        signIn()
        val id = runBlocking {
            c.drafts.insert(
                Draft(
                    title = "What I read in April", body = "A few books, a few essays.", categories = listOf("Writing"),
                    extraFrontMatter = "# Kept by hand: the theme reads this for the post card.\nimage: /assets/img/books.png\nexcerpt: Four books and an essay",
                    editingPath = "_posts/2025-04-20-reading-list.md", baseSha = "x",
                ),
            )
        }
        val vm = EditorViewModel(c, id)
        shoot("08-front-matter", ready = { vm.text != null && vm.state.value.draft != null }, act = {
            compose.onNode(hasContentDescription("Show front matter")).performClick()
        }) { EditorScreen(vm) {} }
    }

    @Test fun categoryPicker() {
        val vm = editorWithDraft()
        shoot("05-category-picker", act = { compose.onNode(hasContentDescription("Add category")).performClick() }) { EditorScreen(vm) {} }
    }

    private fun editingAPost(): EditorViewModel {
        signIn()
        val id = runBlocking {
            c.drafts.insert(
                Draft(
                    title = "What I read in April", body = "A few books, a few essays.", categories = listOf("Writing"),
                    editingPath = "_posts/2025-04-20-reading-list.md", baseSha = "x", extraFrontMatter = "",
                ),
            )
        }
        return EditorViewModel(c, id)
    }

    @Test fun editMenu() {
        val vm = editingAPost()
        shoot("09-edit-menu", ready = { vm.text != null && vm.state.value.draft != null }, act = {
            compose.onNode(hasContentDescription("More")).performClick()
        }) { EditorScreen(vm) {} }
    }

    @Test fun deleteFromBlog() {
        val vm = editingAPost()
        shoot("10-delete-from-blog", ready = { vm.text != null && vm.state.value.draft != null }, act = {
            compose.onNode(hasContentDescription("More")).performClick()
            compose.waitForIdle()
            compose.onNode(hasText("Delete from the blog")).performClick()
        }) { EditorScreen(vm) {} }
    }

    @Test fun photoMenu() {
        val vm = editorWithDraft()
        shoot("11-photo-menu", act = { compose.onNode(hasContentDescription("Add a photo")).performClick() }) { EditorScreen(vm) {} }
    }

    @Test fun search() {
        signIn()
        val vm = HomeViewModel(c)
        shoot("12-search", ready = { vm.state.value.onBlog.isNotEmpty() && !vm.state.value.refreshing }, act = {
            compose.onNode(hasContentDescription("Search your posts")).performClick()
            compose.waitForIdle()
            compose.onNode(androidx.compose.ui.test.hasTestTag("search")).performTextInput("writ")
        }) { HomeScreen(vm, onOpenDraft = {}, onSettings = {}) }
    }
}
