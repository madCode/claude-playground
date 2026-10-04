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
            val bytes = resolver.openInputStream(uri)!!.use { it.readBytes() }
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

/** Just enough of the GIF format to drop the blocks that carry metadata rather than pictures. */
internal object Gif {
    /**
     * The GIF without comment extensions or application extensions other than the animation loop
     * count (NETSCAPE2.0). A file that doesn't parse as expected is refused rather than passed on.
     */
    fun withoutMetadata(gif: ByteArray): ByteArray {
        require(gif.size >= 13 && String(gif, 0, 3) == "GIF") { "Not a GIF" }
        val out = java.io.ByteArrayOutputStream(gif.size)
        var i = 13
        val flags = gif[10].toInt() and 0xff
        if (flags and 0x80 != 0) i += 3 * (1 shl ((flags and 0x07) + 1))
        out.write(gif, 0, i)
        fun skipSubBlocks(from: Int): Int {
            var j = from
            while (true) {
                require(j < gif.size) { "A GIF cut short" }
                val n = gif[j].toInt() and 0xff
                j += 1 + n
                if (n == 0) return j
            }
        }
        while (i < gif.size) {
            when (gif[i].toInt() and 0xff) {
                0x3B -> { out.write(0x3B); return out.toByteArray() }
                0x21 -> {
                    val label = gif[i + 1].toInt() and 0xff
                    val end = skipSubBlocks(i + 2)
                    val keep = when (label) {
                        0xFE -> false
                        0xFF -> String(gif, i + 3, minOf(11, gif.size - i - 3)) == "NETSCAPE2.0"
                        else -> true
                    }
                    if (keep) out.write(gif, i, end - i)
                    i = end
                }
                0x2C -> {
                    var j = i + 10
                    val f = gif[i + 9].toInt() and 0xff
                    if (f and 0x80 != 0) j += 3 * (1 shl ((f and 0x07) + 1))
                    j += 1 // LZW minimum code size
                    val end = skipSubBlocks(j)
                    out.write(gif, i, end - i)
                    i = end
                }
                else -> throw IllegalArgumentException("Not a GIF this app can read")
            }
        }
        throw IllegalArgumentException("A GIF cut short")
    }
}
