package com.app.carry

/**
 * What [SessionWatcher.watchOnce] needs to remember between runs, behind an
 * interface for the same reason [Folder] is: so the decision can be tested
 * without Android.
 *
 * `names()` returns null when nothing has ever been recorded — a first run,
 * or a reinstall. [NewSession.pick] treats that as "announce nothing".
 */
interface Seen {
    fun names(): Set<String>?
    fun remember(names: Set<String>)
}

object SessionWatcher {

    /**
     * One pass: look at the folder, decide, announce, record.
     *
     * Takes its folder, its memory, the date and the way to announce, so
     * every rule below is reachable from a plain JVM test. The Android
     * parts — WorkManager, the channel, the permission — live in
     * [SessionWatchWorker], which is a shell around this.
     *
     * Returns what it announced, which is what the tests assert on.
     */
    fun watchOnce(
        folder: Folder,
        seen: Seen,
        today: String,
        post: (List<NewSession.Found>) -> Unit,
    ): List<NewSession.Found> {
        // A folder nobody picked, or one whose grant is gone, must not
        // touch the record of what was seen. `list()` returns an empty
        // list for both, and an empty list is indistinguishable from an
        // empty folder -- so recording it would wipe the memory, and every
        // file the owner already has would look new the next time the grant
        // came back. That is the "notify everything" failure arriving by a
        // side door.
        if (!folder.chosen) return emptyList()

        val names = folder.list().map { (_, name) -> name }
        val found = NewSession.pick(seen.names(), names, today)
        if (found.isNotEmpty()) post(found)

        // Recorded whether or not the announcement could be delivered. If
        // notifications are off, the sheet is still on the Before tab, and
        // the alternative -- holding the name back until a notification
        // succeeds -- means turning the permission on later produces a
        // burst about sessions already logged.
        seen.remember(names.toSet())
        return found
    }
}
