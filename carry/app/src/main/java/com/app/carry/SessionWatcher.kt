package com.app.carry

import java.time.LocalDate

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
     * How far back the record keeps names. It only needs to be long enough
     * that a file cannot be announced twice, and the folder holds a handful
     * of files, so this is generous.
     */
    private const val KEEP_DAYS = 60L

    /**
     * One pass: look at the folder, decide, record, announce.
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
        today: LocalDate,
        post: (List<NewSession.Found>) -> Unit,
    ): List<NewSession.Found> {
        // A folder nobody picked, or one whose grant is gone, must not
        // touch the record: `list()` returns an empty list for both, and an
        // empty list cannot be told apart from an empty folder.
        if (!folder.chosen) return emptyList()

        val names = folder.list().map { (_, name) -> name }
        val found = NewSession.pick(seen.names(), names, today.toString())

        seen.remember(record(seen.names(), names, today))

        // AFTER recording, deliberately. Anything that throws while posting
        // -- a channel that will not create, a notification too large --
        // must not leave the file unrecorded, or the same sheet is
        // announced again every hour for the rest of the day.
        if (found.isNotEmpty()) post(found)
        return found
    }

    /**
     * The record to keep, given what was kept before and what is in the
     * folder now.
     *
     * Three rules, each from a way the obvious version ("store the current
     * listing") gets it wrong:
     *
     * A name dated AFTER today is not recorded. The runner writes tomorrow's
     * sheet the evening before -- which is the case the whole feature exists
     * for, "the file lands hours before the class" -- and storing it on
     * sight means that when its day arrives it is already seen and is
     * silently never announced.
     *
     * The old record is kept rather than replaced. `SessionFolder.list()`
     * answers an empty list when the query throws, the provider is
     * restarting, or the volume is not mounted yet, and that is
     * indistinguishable from an empty folder. Replacing on that answer wipes
     * the memory, and the next successful run announces today's sheet a
     * second time. Union also covers a file a sync removes and restores.
     *
     * Names are pruned by date, so the union cannot grow for ever.
     */
    private fun record(before: Set<String>?, now: List<String>, today: LocalDate): Set<String> {
        val cutoff = today.minusDays(KEEP_DAYS).toString()
        val eligible = now.mapNotNull(NewSession::parse)
            .filter { it.date <= today.toString() }
            .map { it.name }
        return ((before ?: emptySet()) + eligible)
            .filter { name -> NewSession.parse(name)?.let { it.date >= cutoff } ?: false }
            .toSet()
    }
}
