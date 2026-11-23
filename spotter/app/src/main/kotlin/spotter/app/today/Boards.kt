package spotter.app.today

import spotter.app.update.Download
import spotter.coach.DayBoard
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.LocalDate

/** What the feed answered for a day. */
sealed interface Fetched {
    data class Board(val board: DayBoard) : Fetched
    /** The feed was reached and has no file for the day: not posted yet. */
    data object NotPosted : Fetched
    /** Offline, a server error, or something that isn't a board: fall back to what's kept. */
    data object Unreachable : Fetched
}

/** The day's boards from the public feed, and the last good ones kept on the phone. */
class Boards(private val dir: File, private val get: (String) -> Download.Answer = { Download.answer(it, DayBoard.MAX_CHARS) }) {
    /**
     * The board for [date] from the feed, kept on the phone when it reads as that day's board.
     * Whatever was kept stays as it was otherwise.
     */
    fun fetch(date: LocalDate): Fetched {
        val answer = try {
            get(url(date))
        } catch (e: IOException) {
            return Fetched.Unreachable
        }
        if (answer.code == HttpURLConnection.HTTP_NOT_FOUND) return Fetched.NotPosted
        val text = answer.text?.takeIf { answer.code == HttpURLConnection.HTTP_OK } ?: return Fetched.Unreachable
        val board = DayBoard.parse(text)?.takeIf { it.date == date } ?: return Fetched.Unreachable
        dir.mkdirs()
        val file = File(dir, "$date.json")
        // Its own name, so two fetches at once never write one file.
        val partial = File.createTempFile("$date.", ".part", dir)
        partial.writeText(text)
        if (!partial.renameTo(file)) partial.delete()
        prune()
        return Fetched.Board(board)
    }

    /** The newest board kept from [date] or before, that still reads as one. */
    fun lastGood(date: LocalDate): DayBoard? =
        kept().filter { !it.first.isAfter(date) }.sortedByDescending { it.first }
            .firstNotNullOfOrNull { (_, file) -> runCatching { DayBoard.parse(file.readText()) }.getOrNull() }

    /** Every board kept that still reads as one, for what they have in common (the review list). */
    fun all(): List<DayBoard> = kept().mapNotNull { (_, file) -> runCatching { DayBoard.parse(file.readText()) }.getOrNull() }

    private fun kept(): List<Pair<LocalDate, File>> =
        dir.listFiles().orEmpty().mapNotNull { f ->
            f.name.removeSuffix(".json").takeIf { f.name.endsWith(".json") }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { it to f }
        }

    // A week of boards is plenty to fall back on.
    private fun prune() {
        kept().sortedByDescending { it.first }.drop(KEEP).forEach { it.second.delete() }
    }

    companion object {
        const val KEEP = 7

        fun url(date: LocalDate) = "https://raw.githubusercontent.com/madCode/claude-playground/carry-boards/boards/$date.json"
    }
}
