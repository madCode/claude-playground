package com.app.jekyllposter.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.app.jekyllposter.core.images.Gif
import com.app.jekyllposter.core.io.readAtMost
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prepares a picked photo for a public blog: turned upright, scaled to at most [maxSide] pixels,
 * and re-encoded. Re-encoding writes only pixels, so the camera's EXIF (GPS location, device,
 * time) doesn't go along; that's the point, as a phone photo's location is often the writer's home.
 */
class ImageImporter(private val resolver: ContentResolver, private val dir: File, private val maxSide: Int = 2000) {
    private companion object {
        const val MAX_GIF = 10_000_000
    }

    data class Prepared(val file: File, val extension: String)

    suspend fun import(uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val type = resolver.getType(uri).orEmpty()
        dir.mkdirs()
        // Animated GIFs would lose their animation as a bitmap: kept, with their comment and
        // application blocks (where XMP, and with it a location, can hide) taken out.
        if (type == "image/gif") {
            // Read no more than the limit plus one byte, so a huge file can't run the phone out of memory.
            val bytes = resolver.openInputStream(uri)!!.use { it.readAtMost(MAX_GIF) }
            require(bytes.size <= MAX_GIF) { "GIFs over ${MAX_GIF / 1_000_000} MB are too big for a blog post" }
            val out = File(dir, "${UUID.randomUUID()}.gif")
            out.writeBytes(Gif.withoutMetadata(bytes))
            return@withContext Prepared(out, "gif")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "Not an image this phone can read" }
        // Decode at the smallest power-of-two size still at least maxSide, to spare memory on 50 MP photos.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Not an image this phone can read")
        val exif = resolver.openInputStream(uri)!!.use { ExifInterface(it) }
        val rotation = exif.rotationDegrees
        // Mirrored orientations (front cameras, some editors) are a flip, then the rotation.
        val flipped = exif.isFlipped
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (flipped) postScale(-1f, 1f)
            postRotate(rotation.toFloat())
        }
        val upright = if (rotation == 0 && scale == 1f && !flipped) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // PNGs (screenshots, diagrams) stay lossless; everything else becomes a JPEG.
        val png = type == "image/png"
        val out = File(dir, "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
        out.outputStream().use { upright.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, it) }
        Prepared(out, if (png) "png" else "jpg")
    }
}
