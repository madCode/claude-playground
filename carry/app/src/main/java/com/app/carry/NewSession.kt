package com.app.carry

/**
 * Which session files, if any, are worth a notification.
 *
 * A pure function of (what was seen last time, what is in the folder now,
 * what today's date is). Deliberately pure: the rules that matter here are
 * about *not* notifying, and they are easier to get wrong than right, so
 * they should be testable without Android, a WorkManager harness or a
 * clock. [SessionWatcher] supplies the three inputs and does the posting.
 */
object NewSession {

    /**
     * `session-2026-10-11-3G.json`. The date and the class come from the
     * NAME rather than the file's contents, which is the whole reason this
     * check is cheap enough to run on a timer: no read, no parse, no
     * decode of a file that may be half-written by the runner at the
     * moment the worker looks.
     *
     * The tag is bounded because a provider may return any display name
     * at all and this string reaches a notification. The `^`/`$` are
     * decorative — [matchEntire] is what anchors it, and removing them
     * changes nothing, which a falsification run showed rather than
     * argued.
     */
    private val NAME = Regex("""^session-(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]{1,8})\.json$""")

    data class Found(val name: String, val date: String, val classTag: String)

    fun parse(name: String): Found? =
        NAME.matchEntire(name)?.let { Found(name, it.groupValues[1], it.groupValues[2]) }

    /**
     * The files to notify about: new since [seen], named for [today].
     *
     * `seen == null` means nothing has ever been recorded — a first run, or
     * a reinstall that lost the set. It returns nothing, on purpose. The
     * design's rule is "prefer re-notifying nothing to notifying
     * everything", because the failure the owner would actually feel is
     * six notifications for sessions they logged weeks ago. The caller
     * still records what it saw, so the NEXT new file notifies normally.
     *
     * More than one is normal rather than an edge case: a day the owner
     * borrows the other block's sheet has a 2G and a 3G file for the same
     * date, and both arrive at once.
     */
    fun pick(seen: Set<String>?, now: List<String>, today: String): List<Found> {
        if (seen == null) return emptyList()
        return now.asSequence()
            .filter { it !in seen }
            .mapNotNull(::parse)
            .filter { it.date == today }
            .sortedBy { it.name }
            .toList()
    }

    /**
     * What the notification says. The class and the date, and nothing else
     * — the owner's numbers do not belong on a lock screen, and this is the
     * one function that decides what does.
     */
    fun text(found: List<Found>): String? = when (found.size) {
        0 -> null
        1 -> "${found[0].classTag} sheet is ready"
        else -> found.joinToString(" and ") { it.classTag } + " sheets are ready"
    }
}
