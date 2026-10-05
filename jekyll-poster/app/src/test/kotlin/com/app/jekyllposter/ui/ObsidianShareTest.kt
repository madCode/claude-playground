package com.app.jekyllposter.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.Shared
import com.app.jekyllposter.core.obsidian.ObsidianNote
import com.app.jekyllposter.data.Account
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import com.app.jekyllposter.ui.editor.EditorViewModel
import com.app.jekyllposter.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class)
class ObsidianShareTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    private fun signIn() = runBlocking {
        app.container.accounts.save(Account("sample", "good-token", "sample", "sample-blog", "main"))
    }

    private fun photo(file: File): File {
        file.parentFile!!.mkdirs()
        file.outputStream().use { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return file
    }

    /** A post started from [text], as a share of it, with its embeds handed to the editor. */
    private fun shared(text: String): Long = runBlocking {
        val note = ObsidianNote.convert(text, null, emptyList()) as ObsidianNote.Result.Converted
        app.container.drafts.insert(Draft(body = note.body)).also { app.container.sharedEmbeds[it] = note.embeds }
    }

    @Test fun aNoteSharedAsAFileArrivesReadyForTheBlog() {
        signIn()
        val file = tmp.newFile("Lunch with Priya.md")
        file.writeText(
            "---\ndate: 2021-06-24\ntags: [food, Priya]\naliases: [lunch]\nfind: [Priya]\nreplace: [a friend]\n---\n" +
                "Priya liked [[What I read in April]]. Next: [[Soup recipes]].\n",
        )
        val home = HomeViewModel(app.container)
        home.startShared(Shared("", emptyList(), Uri.fromFile(file)))
        // Not runBlocking: the database finishes on the main looper, which that would block.
        idleUntil(10_000) { home.opened.value != null }
        val id = home.opened.value!!
        val draft = runBlocking { app.container.drafts.get(id) }!!
        assertEquals("Lunch with a friend", draft.title)
        assertEquals(listOf("food", "a friend"), draft.tags)
        assertEquals("a friend liked [What I read in April]({{ site.baseurl }}{% post_url 2025-04-20-reading-list %}). Next: [[Soup recipes]].\n", draft.body)
        assertNull(draft.extraFrontMatter)
        // A bare day, as Jekyll reads it: midnight in the site's time zone (Los Angeles).
        assertEquals("2021-06-24T00:00-07:00", draft.noteDate)
        assertFalse(draft.toString(), draft.toString().contains("Priya"))
    }

    @Test fun photosInTheNoteComeFromTheVaultFolder() {
        val vault = tmp.newFolder("vault")
        photo(File(vault, "attachments/cat.jpg"))
        runBlocking { app.container.settings.setObsidianVault(Uri.fromFile(vault).toString()) }
        val id = shared("Look:\n\n![[cat.jpg|A cat asleep]]\n\n![[gone.png]]\n")
        val editor = EditorViewModel(app.container, id)
        idleUntil(10_000) { editor.text?.images?.size == 1 && !editor.state.value.addingPhoto && editor.state.value.photoError != null }
        val sitePath = editor.text!!.images.single().sitePath
        assertEquals("Look:\n\n![A cat asleep]({{ '$sitePath' | relative_url }})\n\n![[gone.png]]\n", editor.text!!.body)
        assertEquals("Not found in your Obsidian vault folder, so left as written: ![[gone.png]]", editor.state.value.photoError)
        // It came with alt text, so nothing to ask.
        assertTrue(editor.state.value.describing.isEmpty())
    }

    @Test fun withoutAVaultFolderThePhotosWaitForOne() {
        val vault = tmp.newFolder("vault")
        photo(File(vault, "cat.jpg"))
        val id = shared("![[cat.jpg]]\n")
        val editor = EditorViewModel(app.container, id)
        idleUntil { editor.state.value.vaultPhotos == 1 }
        assertTrue(editor.text!!.images.isEmpty())
        editor.vaultChosen(Uri.fromFile(vault))
        idleUntil(10_000) { editor.text?.images?.size == 1 && !editor.state.value.addingPhoto }
        assertEquals(0, editor.state.value.vaultPhotos)
        assertTrue(editor.text!!.body.startsWith("![]({{ '/assets/images/"))
        assertEquals(listOf(editor.text!!.images.single().sitePath), editor.state.value.describing)
        // Kept for the next note.
        assertEquals(Uri.fromFile(vault).toString(), runBlocking { app.container.settings.obsidianVault() })
    }
}
