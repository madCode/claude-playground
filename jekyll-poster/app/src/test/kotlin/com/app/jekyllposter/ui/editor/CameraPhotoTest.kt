package com.app.jekyllposter.ui.editor

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.jekyllposter.data.Draft
import com.app.jekyllposter.testutil.TestApp
import com.app.jekyllposter.testutil.idleUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun editor(): EditorViewModel {
        val id = runBlocking { app.container.drafts.insert(Draft(body = "At the market:")) }
        return EditorViewModel(app.container, id).also { vm -> idleUntil { vm.text != null } }
    }

    /** What the camera app does with the address it's given: writes a JPEG there. */
    private fun shoot() = cameraDir.listFiles()!!.single().outputStream().use {
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
    }

    @Test fun aPhotoTakenGoesInAndItsOriginalIsDeleted() {
        val vm = editor()
        val target = vm.cameraTarget()!!
        // Offered to the camera app through the FileProvider, never as a file: URI.
        assertEquals("content", target.scheme)
        assertEquals("${app.packageName}.camera", target.authority)
        File(cameraDir, target.lastPathSegment!!).createNewFile()
        shoot()
        vm.photoTaken(true)
        idleUntil(10_000) { vm.text?.images?.size == 1 && !vm.state.value.addingPhoto }
        assertTrue(vm.text!!.body.contains(vm.text!!.images.single().sitePath))
        // The original, with its EXIF and location, doesn't stay on the phone.
        idleUntil { cameraDir.listFiles()!!.isEmpty() }
    }

    @Test fun aCancelledPhotoLeavesNothing() {
        val vm = editor()
        vm.cameraTarget()!!
        vm.photoTaken(false)
        assertEquals("At the market:", vm.text!!.body)
        assertFalse(cameraDir.listFiles().orEmpty().any())
    }

    @Test fun noCameraAppSaysSo() {
        val vm = editor()
        vm.cameraTarget()
        vm.cameraUnavailable()
        assertEquals("No camera app to take a photo with.", vm.state.value.photoError)
    }
}
