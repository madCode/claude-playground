package com.app.jekyllposter.sender

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Shares one thing into [EXTRA_TO]'s app, as another app's share sheet does: `photo`, a JPEG
 * with a GPS location in its EXIF, or `note`, an Obsidian-style Markdown file
 * holding [EXTRA_TEXT], Base64 so it survives the shell.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val to = intent.getStringExtra(EXTRA_TO) ?: return finish()
        val dir = File(filesDir, "shared").apply { mkdirs() }
        val (file, type) = when (intent.getStringExtra(EXTRA_WHAT)) {
            "photo" -> photo(File(dir, "harbour.jpg")) to "image/jpeg"
            "note" -> File(dir, "$NOTE_NAME.md").apply { writeBytes(Base64.decode(intent.getStringExtra(EXTRA_TEXT).orEmpty(), Base64.DEFAULT)) } to "text/markdown"
            else -> return finish()
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(type).setPackage(to)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // The grant travels with the ClipData, as the system's share sheet sends it.
        send.clipData = ClipData.newRawUri(null, uri)
        startActivity(send)
        finish()
    }

    private fun photo(file: File): File {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0, 137, 140)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        // The Greenwich observatory: a public place, so the test's location gives nothing away.
        ExifInterface(file).apply {
            setLatLong(51.4769, -0.0005)
            setAttribute(ExifInterface.TAG_MAKE, "Sender")
            saveAttributes()
        }
        return file
    }

    companion object {
        const val EXTRA_TO = "to"
        const val EXTRA_WHAT = "what"
        const val EXTRA_TEXT = "text64"
        const val NOTE_NAME = "Lunch at the market"
    }
}
