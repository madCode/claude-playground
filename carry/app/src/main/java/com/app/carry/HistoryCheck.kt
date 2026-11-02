package com.app.carry

import com.app.carry.brain.History
import com.app.carry.brain.planImport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Phase 4 of the brain move (docs/designs/brain-move.md): the phone keeps its
 * own history, beside the laptop's, and says where the two differ.
 *
 * gym-coach copies its history to `brain/history.jsonl` after each import. The
 * first time, the phone's history (`brain/history-phone.json`) starts as a
 * copy of it. After that the phone imports the finished session files itself,
 * with the ported import, and writes `brain/history-compare.json`: the rows
 * only one side has and the fields that differ. The laptop's history is still
 * the record; nothing here is shown or read by the coach.
 */
object HistoryCheck {
    const val LAPTOP = "history.jsonl"
    /** `.json`, one document: a provider names a file by its type, and has none for `.jsonl`. */
    const val PHONE = "history-phone.json"
    const val REPORT = "history-compare.json"

    /** Session files imported each run: older ones are already in both histories. */
    const val WINDOW_DAYS = 45L

    private val INPUT = Regex("""^input-\d{4}-\d{2}-\d{2}-[A-Za-z0-9]+\.json$""")

    /** What a run did, for tests and the log. */
    enum class Outcome { NO_LAPTOP_HISTORY, NO_KNOWLEDGE_BASE, UNREADABLE, CHECKED, NOT_WRITTEN }

    fun runOnce(sessions: Folder, brain: BrainFolder, today: LocalDate, build: String): Outcome {
        val files = brain.list().associate { (id, name) -> name to id }
        val laptopId = files[LAPTOP] ?: return Outcome.NO_LAPTOP_HISTORY
        val laptopText = brain.read(laptopId) ?: return Outcome.UNREADABLE
        val laptop = runCatching { History.parse(laptopText) }.getOrElse { return Outcome.UNREADABLE }

        // The laptop's knowledge base, as the newest sheet input carries it, so an added row is
        // matched as the laptop's import matches it. Only the newest: an older one would match
        // with a knowledge base the laptop no longer has, and the report says which was used.
        val kbFrom = files.keys.filter { INPUT.matches(it) }.maxOrNull() ?: return Outcome.NO_KNOWLEDGE_BASE
        val kb = brain.read(files.getValue(kbFrom))?.let { runCatching { History.kbFromInput(it) }.getOrNull() }
            ?: return Outcome.NO_KNOWLEDGE_BASE

        // Seeded from the laptop's once, and again only when the phone's copy is there but no
        // longer one whole document (a write cut short). A copy that can't be READ right now
        // is a busy provider, not damage: nothing is replaced, and the next run tries again.
        val phoneId = files[PHONE]
        val phoneText = phoneId?.let { brain.read(it) ?: return Outcome.UNREADABLE }
        val stored = phoneText?.let { runCatching { History.parsePhone(it) }.getOrNull() }
        val seeded = stored == null
        // Rows from before ids existed are the laptop's alone: only it ever writes them, and its
        // `adopt-history` gives them ids later. Mirrored each run, so an adoption reaches the
        // phone instead of standing as a difference for good.
        val phone = laptop.filterNot(History::keyed) + (stored ?: laptop).filter(History::keyed)

        val names = sessions.list().associate { (id, name) -> name to id }
        val finished = History.finished(names.keys.toList(), today, WINDOW_DAYS)
        val sources = finished.map { it to History.source(sessions.readBytes(names.getValue(it), History.MAX_SESSION_FILE_BYTES)) }
        val plan = planImport(sources, phone, kb, write = true)
        val after = plan.rewritten ?: (phone + plan.appended)

        val phoneWritten = when {
            phoneId == null -> brain.create(PHONE, History.writePhone(after))
            seeded || after != stored -> brain.write(phoneId, History.writePhone(after))
            else -> true
        }
        val report = JsonObject(History.compare(after, laptop) + mapOf(
            "brain" to JsonPrimitive(build),
            "checked" to JsonPrimitive(today.toString()),
            "seeded" to JsonPrimitive(seeded),
            "phone_written" to JsonPrimitive(phoneWritten),
            "knowledge_base_from" to JsonPrimitive(kbFrom),
            "files_imported" to JsonPrimitive(finished.size),
            "import" to JsonArray(plan.report.filterNotNull().map(::JsonPrimitive)),
        ))
        val reportText = report.toString() + "\n"
        val reportWritten = files[REPORT]?.let { brain.write(it, reportText) } ?: brain.create(REPORT, reportText)
        return if (phoneWritten && reportWritten) Outcome.CHECKED else Outcome.NOT_WRITTEN
    }
}
