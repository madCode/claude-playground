package com.app.jekyllposter.ui.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import com.app.jekyllposter.ui.theme.PosterStyle
import com.app.jekyllposter.ui.theme.PosterStyles
import com.app.jekyllposter.ui.theme.PosterTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

/**
 * A design round: every style in [PosterStyles.all] on the main screens, light and dark, as PNGs
 * in build/design for comparing side by side. Not part of the build; run with
 * `-Pdesign` (see app/build.gradle.kts).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class DesignRoundTest(styleName: String, private val dark: Boolean) {
    private val style: PosterStyle = PosterStyles.all.first { it.name == styleName }
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val c = app.container
    private val out = File(System.getProperty("designDir") ?: "build/design").apply { mkdirs() }

    @After fun close() = app.github.close()

    private fun name(screen: String) = "${style.name}-${if (dark) "dark" else "light"}-$screen"

    private fun signIn() = runBlocking {
        c.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
        c.blogs.refresh()
    }

    private fun shoot(screen: String, ready: () -> Boolean = { true }, act: () -> Unit = {}, content: @Composable () -> Unit) {
        assumeTrue(System.getProperty("designDir") != null)
        compose.setContent { PosterTheme(dark = dark, style = style, content = content) }
        idleUntil(condition = ready)
        compose.waitForIdle()
        act()
        compose.waitForIdle()
        File(out, "${name(screen)}.png").outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    @Test fun home() {
        signIn()
        runBlocking {
            c.drafts.insert(Draft(title = "Notes on the new bike lane", body = "Half written.", categories = listOf("cycling")))
            c.drafts.insert(Draft(title = "Sourdough, round four", state = PostState.Published, buildState = BuildState.Live))
        }
        val vm = HomeViewModel(c)
        shoot("home", ready = { vm.state.value.onBlog.isNotEmpty() && !vm.state.value.refreshing }) { HomeScreen(vm, {}, {}) }
    }

    @Test fun editor() {
        signIn()
        val id = runBlocking {
            c.drafts.insert(
                Draft(
                    title = "A walk to the lighthouse",
                    body = "Out past the harbour wall at **low tide**, the path runs along the rocks for a mile.\n\n> The sea was the colour of a slate roof.\n\nWe turned back at the second stile.",
                    categories = listOf("travel"), tags = listOf("walking", "weekend"),
                ),
            )
        }
        val vm = EditorViewModel(c, id)
        shoot("editor", ready = { vm.text != null && vm.state.value.draft != null }) { EditorScreen(vm) {} }
    }

    @Test fun picker() {
        signIn()
        val id = runBlocking { c.drafts.insert(Draft(title = "A walk to the lighthouse", categories = listOf("travel"))) }
        val vm = EditorViewModel(c, id)
        idleUntil { vm.text != null && vm.state.value.taxonomy.categories.isNotEmpty() }
        shoot("picker", act = { compose.onNode(hasContentDescription("Add category")).performClick() }) { EditorScreen(vm) {} }
    }

    @Test fun connect() {
        val vm = ConnectViewModel(c)
        shoot("connect") { ConnectScreen(vm) {} }
    }

    companion object {
        @JvmStatic
        // Plain names: they become report file names.
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-dark={1}")
        fun styles(): List<Array<Any>> = PosterStyles.all.flatMap { listOf(arrayOf<Any>(it.name, false), arrayOf<Any>(it.name, true)) }
    }
}
