package com.app.jekyllposter.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.PendingShare
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class)
class SharedPhotosTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app = ApplicationProvider.getApplicationContext<TestApp>()

    @After fun close() = app.github.close()

    private fun photo(name: String): Uri {
        val file = tmp.newFile(name)
        file.outputStream().use { Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test fun photosSharedFromTheGalleryGoInInOrderAfterTheText() {
        val id = runBlocking { app.container.drafts.insert(Draft(body = "From the gallery:")) }
        app.container.pendingShares[id] = PendingShare(photos = listOf(photo("a.jpg"), photo("b.jpg")))
        val editor = EditorViewModel(app.container, id)
        idleUntil(10_000) { editor.text?.images?.size == 2 && !editor.state.value.addingPhoto }
        val body = editor.text!!.body
        assertTrue(body, body.startsWith("From the gallery:\n\n![]({{ '/assets/images/"))
        val first = body.indexOf(editor.text!!.images[0].sitePath)
        val second = body.indexOf(editor.text!!.images[1].sitePath)
        assertTrue(first in 0 until second)
        assertEquals(2, Regex("""!\[]""").findAll(body).count())
        // Each asks for alt text, first first.
        val first2 = editor.text!!.images[0].sitePath
        assertEquals(listOf(first2, editor.text!!.images[1].sitePath), editor.state.value.describing)
        editor.describe(first2, "A red square")
        assertTrue(editor.text!!.body.contains("![A red square]({{ '$first2' | relative_url }})"))
        idleUntil { editor.state.value.describing.size == 1 }
    }
}
