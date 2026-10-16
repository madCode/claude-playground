package com.app.carry

import java.time.LocalDate
import java.time.LocalDateTime

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
     * The earliest hour a notification may be posted.
     *
     * The runner writes tomorrow's sheet the evening before, so the first
     * run after local midnight is the one that finds it new and dated
     * today. Posting there means a sound at 00-something for a class many
     * hours away -- and worse, a notice dismissed, swiped or buried by
     * morning is never posted again, on what is now the only path that
     * tells the owner their sheet arrived.
     *
     * A sheet found before this hour is held and NOT recorded, so the
     * morning run announces it normally.
     */
    internal const val EARLIEST_HOUR = 6

    /** How long before its class a sheet that says when the class starts is announced. The
     *  check is hourly, so in practice this is "within the hour after that moment". */
    internal const val LEAD_MINUTES = 90L

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
        now: LocalDateTime,
        post: (List<NewSession.Found>) -> Unit,
    ): List<NewSession.Found> {
        val today = now.toLocalDate()
        // A folder nobody picked, or one whose grant is gone, must not
        // touch the record: `list()` returns an empty list for both, and an
        // empty list cannot be told apart from an empty folder.
        if (!folder.chosen) return emptyList()

        val listing = folder.list()
        val names = listing.map { (_, name) -> name }
        val found = NewSession.pick(seen.names(), names, today.toString())

        // Too early to tell anyone, so that sheet is neither announced nor
        // remembered -- recording it would spend the announcement on an hour
        // the owner may be asleep for, and a later run says it instead. Each
        // sheet is held on its own: one whose class is soon is not kept
        // waiting for another's.
        val ids = listing.associate { (id, name) -> name to id }
        val (due, held) = found.partition { due(it, now) { ids[it.name]?.let(folder::read) } }

        // Read again: a provider read can block, for longer than the hour
        // between runs, and a later run may have announced and recorded one
        // of these meanwhile. Against the stale record this run would
        // announce it a second time, or un-record it so the next one did.
        val fresh = seen.names()
        val ready = due.filter { fresh?.contains(it.name) != true }
        val stillHeld = held.filter { fresh?.contains(it.name) != true }.map { it.name }.toSet()
        seen.remember(record(fresh, names, today) - stillHeld)

        // AFTER recording, deliberately. Anything that throws while posting
        // -- a channel that will not create, a notification too large --
        // must not leave the file unrecorded, or the same sheet is
        // announced again every hour for the rest of the day.
        if (ready.isNotEmpty()) post(ready)
        return ready
    }

    /**
     * Whether a sheet may be announced now: from [LEAD_MINUTES] before its class when the file
     * says when that is (`starts_at`, written by the runner from the calendar), else from
     * [EARLIEST_HOUR]. The hour stays as the fallback for every file without the field --
     * backfills, hand-written days and everything written before it existed -- or those could
     * never be announced at all.
     */
    internal fun due(found: NewSession.Found, now: LocalDateTime, text: () -> String?): Boolean {
        val start = startsAt(text(), found.date) ?: return now.hour >= EARLIEST_HOUR
        return !now.isBefore(start.minusMinutes(LEAD_MINUTES))
    }

    /** The class's start from a session file, or null when it has none, it can't be read, or it
     *  isn't on the file's own date, which would be the runner's mistake, not a time to trust. */
    internal fun startsAt(text: String?, date: String): LocalDateTime? = runCatching {
        val value = Session.json.parseToJsonElement(text ?: return null)
            .let { it as? kotlinx.serialization.json.JsonObject }
            ?.get("starts_at")
            ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
            ?.takeIf { it.isString }?.content ?: return null
        LocalDateTime.parse(value).takeIf { it.toLocalDate().toString() == date }
    }.getOrNull()

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
        val parsed = now.mapNotNull(NewSession::parse)

        // On a FIRST run -- a fresh install, a reinstall that lost the set,
        // or simply the first time this feature ever ran on an existing
        // install -- today's files are left OUT of the record, so the next
        // run announces them.
        //
        // The anti-burst rule this refines is still intact, because the
        // burst it prevents is entirely OLD sessions: "six notifications
        // for sessions logged weeks ago" was the failure, and those are
        // all recorded and silenced here exactly as before. Silencing
        // TODAY's was never the point, and doing it swallowed the one
        // sheet the owner cares about. Found by a live test on the owner's
        // own phone (2026-10-16): the test file synced, appeared on the
        // Before tab, and could never have been announced, because the
        // first run was always going to record it first.
        //
        // A borrowed day puts two files at today's date, so this can
        // announce two at once on a first run. That is correct rather than
        // a burst: both are sheets they are about to walk in with.
        val eligible = parsed
            .filter { it.date <= today.toString() }
            .filter { before != null || it.date != today.toString() }
            .map { it.name }

        return ((before ?: emptySet()) + eligible)
            .filter { name -> NewSession.parse(name)?.let { it.date >= cutoff } ?: false }
            .toSet()
    }
}
