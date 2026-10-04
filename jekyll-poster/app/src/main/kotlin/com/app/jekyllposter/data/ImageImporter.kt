package com.app.jekyllposter.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Prepares a picked photo for a public blog: turned upright, scaled to at most [maxSide] pixels,
 * and re-encoded. Re-encoding writes only pixels, so the camera's EXIF (GPS location, device,
 * time) doesn't go along; that's the point, as a phone photo's location is often the writer's home.
 */
class ImageImporter(private val resolver: ContentResolver, private val dir: File, private val maxSide: Int = 2000) {
    data class Prepared(val file: File, val extension: String)

    suspend fun import(uri: Uri): Prepared = withContext(Dispatchers.IO) {
        val type = resolver.getType(uri).orEmpty()
        dir.mkdirs()
        // Animated GIFs would lose their animation as a bitmap, and carry no EXIF; copied as they are.
        if (type == "image/gif") {
            val out = File(dir, "${UUID.randomUUID()}.gif")
            resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
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
        val rotation = resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
        val upright = if (rotation == 0 && scale == 1f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // PNGs (screenshots, diagrams) stay lossless; everything else becomes a JPEG.
        val png = type == "image/png"
        val out = File(dir, "${UUID.randomUUID()}.${if (png) "png" else "jpg"}")
        out.outputStream().use { upright.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, it) }
        Prepared(out, if (png) "png" else "jpg")
    }
}
