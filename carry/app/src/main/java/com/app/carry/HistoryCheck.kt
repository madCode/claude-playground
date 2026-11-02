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
 * first time, the phone's history (`brain/history-phone.jsonl`) starts as a
 * copy of it. After that the phone imports the finished session files itself,
 * with the ported import, and writes `brain/history-compare.json`: the rows
 * only one side has and the fields that differ. The laptop's history is still
 * the record; nothing here is shown or read by the coach.
 */
object HistoryCheck {
    const val LAPTOP = "history.jsonl"
    const val PHONE = "history-phone.jsonl"
    const val REPORT = "history-compare.json"

    private val INPUT = Regex("""^input-\d{4}-\d{2}-\d{2}-[A-Za-z0-9]+\.json$""")

    /** What a run did, for tests and the log. */
    enum class Outcome { NO_LAPTOP_HISTORY, NO_KNOWLEDGE_BASE, UNREADABLE, CHECKED, NOT_WRITTEN }

    fun runOnce(sessions: Folder, brain: BrainFolder, today: LocalDate, build: String): Outcome {
        val files = brain.list().associate { (id, name) -> name to id }
        val laptopId = files[LAPTOP] ?: return Outcome.NO_LAPTOP_HISTORY
        val laptopText = brain.read(laptopId) ?: return Outcome.UNREADABLE
        val laptop = runCatching { History.parse(laptopText) }.getOrElse { return Outcome.UNREADABLE }

        // The laptop's knowledge base, as the newest sheet input carries it, so an added row
        // is matched here as the laptop's import matches it.
        val kb = files.keys.filter { INPUT.matches(it) }.sortedDescending().asSequence()
            .mapNotNull { name -> brain.read(files.getValue(name))?.let { runCatching { History.kbFromInput(it) }.getOrNull() } }
            .firstOrNull() ?: return Outcome.NO_KNOWLEDGE_BASE

        // Seeded from the laptop's once. Seeded again only when the phone's own copy no longer
        // reads as a history, which a write cut short can leave; the report says so.
        val phoneId = files[PHONE]
        val stored = phoneId?.let { brain.read(it) }?.let { runCatching { History.parse(it) }.getOrNull() }
        val seeded = stored == null
        val phone = stored ?: laptop

        val names = sessions.list().associate { (id, name) -> name to id }
        val finished = History.finished(names.keys.toList(), today)
        val sources = finished.map { it to History.source(sessions.readBytes(names.getValue(it), History.MAX_SESSION_FILE_BYTES)) }
        val plan = planImport(sources, phone, kb, write = true)
        val after = plan.rewritten ?: (phone + plan.appended)

        val historyText = History.write(after)
        val phoneWritten = when {
            phoneId == null -> brain.create(PHONE, historyText)
            seeded || after != phone -> brain.write(phoneId, historyText)
            else -> true
        }
        val report = JsonObject(History.compare(after, laptop) + mapOf(
            "brain" to JsonPrimitive(build),
            "checked" to JsonPrimitive(today.toString()),
            "seeded" to JsonPrimitive(seeded),
            "phone_written" to JsonPrimitive(phoneWritten),
            "import" to JsonArray(plan.report.filterNotNull().map(::JsonPrimitive)),
        ))
        val reportText = report.toString() + "\n"
        val reportWritten = files[REPORT]?.let { brain.write(it, reportText) } ?: brain.create(REPORT, reportText)
        return if (phoneWritten && reportWritten) Outcome.CHECKED else Outcome.NOT_WRITTEN
    }
}
