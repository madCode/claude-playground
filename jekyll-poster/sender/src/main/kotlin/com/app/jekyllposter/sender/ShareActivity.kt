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
 * Hands something to [EXTRA_TO]'s app, as another app's share sheet or "Open with" does:
 * - `photo`, a JPEG with a GPS location in its EXIF; `photos`, two of them in one share;
 * - `note`, an Obsidian-style Markdown file holding [EXTRA_TEXT] (Base64, so it survives the
 *   shell); `notetext`, the same as a plain-text file; `open`, the Markdown file opened with the app.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val to = intent.getStringExtra(EXTRA_TO) ?: return finish()
        val dir = File(filesDir, "shared").apply { mkdirs() }
        fun note(name: String) = File(dir, name).apply { writeBytes(Base64.decode(intent.getStringExtra(EXTRA_TEXT).orEmpty(), Base64.DEFAULT)) }
        fun uriOf(file: File) = FileProvider.getUriForFile(this, "$packageName.files", file)
        val grant = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        val send = when (intent.getStringExtra(EXTRA_WHAT)) {
            "photo" -> sendOne(uriOf(photo(File(dir, "harbour.jpg"))), "image/jpeg")
            "photos" -> {
                val uris = arrayListOf(uriOf(photo(File(dir, "harbour.jpg"))), uriOf(photo(File(dir, "pier.jpg"), Color.rgb(194, 24, 91))))
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    .apply { clipData = ClipData.newRawUri(null, uris[0]).apply { addItem(ClipData.Item(uris[1])) } }
            }
            "note" -> sendOne(uriOf(note("$NOTE_NAME.md")), "text/markdown")
            "notetext" -> sendOne(uriOf(note("$NOTE_NAME.txt")), "text/plain")
            "open" -> Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(note("$NOTE_NAME.md")), "text/markdown")
            else -> return finish()
        }
        startActivity(send.setPackage(to).addFlags(grant))
        finish()
    }

    // The grant travels with the ClipData, as the system's share sheet sends it.
    private fun sendOne(uri: android.net.Uri, type: String) =
        Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri).apply { clipData = ClipData.newRawUri(null, uri) }

    private fun photo(file: File, colour: Int = Color.rgb(0, 137, 140)): File {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(colour) }
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
