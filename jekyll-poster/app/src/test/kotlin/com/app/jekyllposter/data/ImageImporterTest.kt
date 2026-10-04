package com.app.jekyllposter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val importer by lazy { ImageImporter(context.contentResolver, tmp.root.resolve("out"), maxSide = 400) }

    /** A landscape camera photo, stored sideways with an orientation tag and a GPS position. */
    private fun cameraPhoto(): Uri {
        val file = tmp.newFile("camera.jpg")
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setLatLong(51.5007, -0.1246)
            setAttribute(ExifInterface.TAG_MAKE, "ExampleCam")
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    @Test fun aPhotoIsTurnedUprightScaledAndStrippedOfItsLocation() = runBlocking {
        val prepared = importer.import(cameraPhoto())
        assertEquals("jpg", prepared.extension)
        val out = BitmapFactory.decodeFile(prepared.file.path)
        // Turned upright: the 1200×800 photo was tagged as rotated, so it's now portrait, scaled to 400.
        assertEquals(267, out.width)
        assertEquals(400, out.height)
        val exif = ExifInterface(prepared.file)
        assertNull(exif.latLong)
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
    }

    @Test fun aSmallPhotoKeepsItsSize() = runBlocking {
        val file = tmp.newFile("small.jpg")
        file.outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val out = BitmapFactory.decodeFile(importer.import(Uri.fromFile(file)).file.path)
        assertEquals(300, out.width)
    }
}
