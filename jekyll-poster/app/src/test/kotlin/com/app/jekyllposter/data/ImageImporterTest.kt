package com.app.jekyllposter.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun aMirroredPhotoIsUnmirrored() = runBlocking {
        val file = tmp.newFile("selfie.jpg")
        // Red on the left, as stored; the tag says to show it mirrored, so red belongs on the right.
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until 200) for (y in 0 until 100) setPixel(x, y, if (x < 100) android.graphics.Color.RED else android.graphics.Color.BLUE)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_FLIP_HORIZONTAL.toString()); saveAttributes() }
        val out = BitmapFactory.decodeFile(importer.import(Uri.fromFile(file)).file.path)
        assertTrue(android.graphics.Color.red(out.getPixel(190, 50)) > 200)
        assertTrue(android.graphics.Color.blue(out.getPixel(10, 50)) > 200)
    }

    @Test fun aGifKeepsItsFramesAndLoopButNotItsComments() {
        // A 1×1 GIF with a loop block, an XMP application block and a comment.
        val header = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 0, 1, 0, 0x80.toByte(), 0, 0, 0, 0, 0, -1, -1, -1)
        val loop = byteArrayOf(0x21, 0xFF.toByte(), 11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0)
        val xmp = byteArrayOf(0x21, 0xFF.toByte(), 11) + "XMP DataXMP".toByteArray() + byteArrayOf(5) + "GPS:1".toByteArray() + byteArrayOf(0)
        val comment = byteArrayOf(0x21, 0xFE.toByte(), 4) + "home".toByteArray() + byteArrayOf(0)
        val image = byteArrayOf(0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 0x01, 0)
        val gif = header + loop + xmp + comment + image + byteArrayOf(0x3B)
        val out = Gif.withoutMetadata(gif)
        assertArrayEquals(header + loop + image + byteArrayOf(0x3B), out)
    }

    @Test fun aSmallPhotoKeepsItsSize() = runBlocking {
        val file = tmp.newFile("small.jpg")
        file.outputStream().use { Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val out = BitmapFactory.decodeFile(importer.import(Uri.fromFile(file)).file.path)
        assertEquals(300, out.width)
    }
}
