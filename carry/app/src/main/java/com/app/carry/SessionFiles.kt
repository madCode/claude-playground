package com.app.carry

/**
 * Which file in the folder is today's, and what it parses to.
 *
 * The choosing and the parsing are pure so they can be tested without a
 * device; everything that needs Android's storage framework lives in
 * [SessionFolder], which is as thin as it can be. SAF behaviour is the one
 * part no JVM test can answer honestly.
 */
object SessionFiles {
    /** The runner writes `session-<date>-<class>.json`. */
    private val NAME = Regex("""^session-(\d{4}-\d{2}-\d{2})-([A-Za-z0-9]+)\.json$""")

    fun isSessionFile(name: String) = NAME.matches(name)

    /**
     * The newest session by the date in its name, not by file timestamp:
     * a sync copies a file whenever it feels like it, so mtime says when it
     * arrived rather than which workout it is. Ties break on the name so
     * the choice is stable rather than arbitrary.
     */
    fun newest(names: List<String>): String? =
        names.filter(::isSessionFile).maxWithOrNull(
            compareBy({ NAME.find(it)!!.groupValues[1] }, { it })
        )

    /**
     * Returns null rather than throwing on anything unreadable. A folder is
     * a shared space -- other things write there, and a half-synced file is
     * normal, not exceptional.
     */
    /** A readable file whose schema we do not know -- "update the app" and
     *  "wait for the sync" need different words. */
    fun looksLikeNewerSchema(text: String): Boolean =
        Regex(""""schema"\s*:\s*"gym-session/(\d+)"""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull()?.let { it > 1 } == true

    fun parse(text: String): Session? = try {
        Session.json.decodeFromString<Session>(text).takeIf { it.schema == Session.SCHEMA }
    } catch (_: IllegalArgumentException) {
        // SerializationException extends this, so one catch covers both.
        null
    }
}
