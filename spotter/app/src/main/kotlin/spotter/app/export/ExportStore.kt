package spotter.app.export

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

/** What the export remembers: the picked folder, what it has written there, and how it last went. */
class ExportStore(private val prefs: SharedPreferences) : ExportRun.Store {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** The picked folder, as Android's document tree address; null until one is picked. */
    var folder: String?
        get() = prefs.getString(FOLDER, null)
        // A new folder has none of the files yet.
        set(value) = prefs.edit().putString(FOLDER, value).remove(WRITTEN).remove(LAST_AT).remove(LAST_OK).apply()

    /** The folder's name as the person knows it ("Download/Spotter"), for the screen. */
    var folderName: String?
        get() = prefs.getString(FOLDER_NAME, null)
        set(value) = prefs.edit().putString(FOLDER_NAME, value).apply()

    override var written: Map<String, String>
        get() = prefs.getString(WRITTEN, null)?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
        set(value) = prefs.edit().putString(WRITTEN, Json.encodeToString(value)).apply()

    /** When the last run finished, in epoch milliseconds, and whether the folder then matched the log. */
    val lastAt: Long get() = prefs.getLong(LAST_AT, 0)
    val lastOk: Boolean get() = prefs.getBoolean(LAST_OK, true)

    fun finished(at: Long, ok: Boolean) = prefs.edit().putLong(LAST_AT, at).putBoolean(LAST_OK, ok).apply()

    /** Calls [onChange] whenever a run or a new folder changes what the screen should say. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val PREFS = "export"
        private const val FOLDER = "folder"
        private const val FOLDER_NAME = "folder-name"
        private const val WRITTEN = "written"
        private const val LAST_AT = "last-at"
        private const val LAST_OK = "last-ok"
    }
}
