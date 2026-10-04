package com.app.jekyllposter.ui.editor

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.data.PostState
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class)
class CameraPhotoTest {
    private val app = ApplicationProvider.getApplicationContext<TestApp>()
    private val cameraDir = File(app.cacheDir, "camera")

    @After fun close() = app.github.close()

    /**
     * FileProvider keeps each authority's paths in a static cache, resolved against the first
     * test's data directory; Robolectric gives every test a new one.
     */
    @Before fun forgetFileProviderPaths() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private fun editor(draft: Draft = Draft(body = "At the market:")): EditorViewModel {
        val id = runBlocking { app.container.drafts.insert(draft) }
        return EditorViewModel(app.container, id).also { vm -> idleUntil { vm.text != null && vm.state.value.draft != null } }
    }

    /** What the camera app does with the address it's given: writes a JPEG there. */
    private fun shoot(path: String) = File(path).outputStream().use {
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
    }

    private fun originals() = cameraDir.listFiles().orEmpty().toList()

    @Test fun aPhotoTakenGoesInAndItsOriginalIsDeleted() {
        val vm = editor()
        val target = vm.cameraTarget()!!
        // Offered to the camera app through the FileProvider, never as a file: URI.
        assertEquals("content", target.uri.scheme)
        assertEquals("${app.packageName}.camera", target.uri.authority)
        shoot(target.path)
        vm.photoTaken(target.path, true)
        idleUntil(10_000) { vm.text?.images?.size == 1 && !vm.state.value.addingPhoto }
        assertTrue(vm.text!!.body.contains(vm.text!!.images.single().sitePath))
        // The original, with its EXIF and location, doesn't stay on the phone.
        idleUntil { originals().isEmpty() }
    }

    @Test fun theAnswerReachesAnEditorRebuiltMeanwhile() {
        // The camera app pushed this one out of memory: the path comes back from saved state.
        val draft = Draft(body = "At the market:")
        val id = runBlocking { app.container.drafts.insert(draft) }
        val target = EditorViewModel(app.container, id).also { vm -> idleUntil { vm.text != null } }.cameraTarget()!!
        shoot(target.path)
        // The answer arrives as the new editor starts, before its draft has loaded.
        val rebuilt = EditorViewModel(app.container, id)
        rebuilt.photoTaken(target.path, true)
        idleUntil(10_000) { rebuilt.text?.images?.size == 1 && !rebuilt.state.value.addingPhoto }
        idleUntil { originals().isEmpty() }
    }

    @Test fun twoTargetsAreTwoFiles() {
        val vm = editor()
        assertTrue(vm.cameraTarget()!!.path != vm.cameraTarget()!!.path)
    }

    @Test fun aCancelledOrEmptyPhotoLeavesNothing() {
        val vm = editor()
        val cancelled = vm.cameraTarget()!!
        val empty = vm.cameraTarget()!!
        shoot(cancelled.path)
        vm.photoTaken(cancelled.path, false)
        // Some camera apps say OK and write nothing.
        vm.photoTaken(empty.path, true)
        assertEquals("At the market:", vm.text!!.body)
        assertTrue(originals().isEmpty())
    }

    @Test fun aPhotoForAPostBeingPublishedIsRefusedAndSaysSo() {
        val vm = editor(Draft(title = "Gone already", state = PostState.Queued))
        val target = vm.cameraTarget()!!
        shoot(target.path)
        vm.photoTaken(target.path, true)
        idleUntil { vm.state.value.photoError != null }
        assertEquals("The photo wasn't added: this post can't be changed now.", vm.state.value.photoError)
        assertTrue(originals().isEmpty())
    }

    @Test fun noCameraAppSaysSo() {
        val vm = editor()
        val target = vm.cameraTarget()!!
        vm.cameraUnavailable(target.path)
        assertEquals("No camera app to take a photo with.", vm.state.value.photoError)
        assertTrue(originals().isEmpty())
    }
}
