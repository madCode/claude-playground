package spotter.app.export

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

/** What the export remembers: the picked folder, what it has written there, and how it last went. */
class ExportStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    /** The picked folder, as Android's document tree address; null until one is picked. */
    var folder: String?
        get() = prefs.getString(FOLDER, null)
        // A new folder has none of the files yet.
        set(value) {
            synchronized(lock) { prefs.edit().putString(FOLDER, value).remove(FOLDER_NAME).remove(WRITTEN).remove(LAST_AT).remove(LAST_OK).commit() }
        }

    /** The folder's name as the person knows it ("Download/Spotter"), for the screen. */
    val folderName: String? get() = prefs.getString(FOLDER_NAME, null)

    /** Records the name of [folder], unless another has been picked since. */
    fun named(folder: String, name: String?) {
        synchronized(lock) {
            if (this.folder == folder && name != null) prefs.edit().putString(FOLDER_NAME, name).commit()
        }
    }

    /**
     * What has been written to [folder], for one run. A run that began before another folder was
     * picked reads and records nothing: its files went to the old folder, and the new one's list
     * must stay empty or the new folder would be taken as already filled.
     */
    fun written(folder: String): ExportRun.Store = object : ExportRun.Store {
        override var written: Map<String, String>
            get() = synchronized(lock) {
                if (this@ExportStore.folder != folder) return emptyMap()
                prefs.getString(WRITTEN, null)?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()
            }
            set(value) {
                synchronized(lock) {
                    if (this@ExportStore.folder == folder) prefs.edit().putString(WRITTEN, Json.encodeToString(value)).commit()
                }
            }
    }

    /** When the last run finished, in epoch milliseconds, and whether the folder then matched the log. */
    val lastAt: Long get() = prefs.getLong(LAST_AT, 0)
    val lastOk: Boolean get() = prefs.getBoolean(LAST_OK, true)

    /** Records how a run to [folder] went, unless another folder has been picked since. */
    fun finished(folder: String, at: Long, ok: Boolean) {
        synchronized(lock) {
            if (this.folder == folder) prefs.edit().putLong(LAST_AT, at).putBoolean(LAST_OK, ok).commit()
        }
    }

    /** Calls [onChange] whenever a run or a new folder changes what the screen should say. */
    fun listen(onChange: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        // One store per process in effect: the screen and the worker each make their own.
        private val lock = Any()
        const val PREFS = "export"
        private const val FOLDER = "folder"
        private const val FOLDER_NAME = "folder-name"
        private const val WRITTEN = "written"
        private const val LAST_AT = "last-at"
        private const val LAST_OK = "last-ok"
    }
}
