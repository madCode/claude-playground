package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Invented rows. */
class HistoryTest {
    @Test
    fun `history reads as the laptop reads it and writes back what it read`() {
        // Blank and space-only lines skipped; a NaN and an integer past 2^64, which the
        // default encoder refuses or rounds, come back exactly as written.
        val text = "{\"row_id\": \"a\", \"weight\": NaN}\n\n   \n{\"row_id\": \"b\", \"x\": 123456789012345678901234567890}\r\n"
        val rows = History.parse(text)
        assertEquals(2, rows.size)
        val again = History.parse(History.write(rows))
        assertEquals("NaN", (again[0]["weight"] as JsonPrimitive).content)
        assertEquals("123456789012345678901234567890", (again[1]["x"] as JsonPrimitive).content)
    }

    @Test(expected = Exception::class)
    fun `a history line that is not an object is refused, not skipped`() {
        History.parse("{\"row_id\": \"a\"}\n[1, 2]\n")
    }

    @Test
    fun `the import takes the session files of days already over, in name order`() {
        val names = listOf(
            "session-2026-01-11-2G.json", "session-2026-01-09-3G.json", "session-2026-01-10-2G.json",
            "session-2026-01-09-2G.json", "notes.txt", "session-2026-1-08-2G.json", "session-2026-01-08-2#G.json",
            // Non-ASCII digits: the Python pattern is ASCII, so these are not session files.
            "session-٢٠٢٦-01-08-2G.json",
        )
        assertEquals(
            listOf("session-2026-01-09-2G.json", "session-2026-01-09-3G.json"),
            History.finished(names, LocalDate.of(2026, 1, 10)),
        )
    }

    @Test
    fun `a session file is decoded strictly, the way the laptop reads it`() {
        assertEquals(ImportSource.Text("{\"a\": \"é\"}"), History.source("{\"a\": \"é\"}".encodeToByteArray()))
        // Invalid UTF-8 is refused, never turned into a replacement character that then imports.
        assertEquals(ImportSource.Failed("UnicodeDecodeError", unreadable = true), History.source(byteArrayOf(0x7b, 0xff.toByte(), 0x7d)))
        assertEquals(ImportSource.Failed("ValueError", unreadable = true), History.source(ByteArray(History.MAX_SESSION_FILE_BYTES + 1)))
        assertEquals(ImportSource.Text(""), History.source(ByteArray(0)))
        assertEquals(ImportSource.Failed("OSError", unreadable = true), History.source(null))
    }

    @Test
    fun `the comparison names what only one side has and which fields differ`() {
        val phone = History.parse(
            """
            {"row_id": "s#0", "weight": 25, "reps": 8}
            {"row_id": "s#1", "weight": 30, "reps": 10}
            {"row_id": "s#2", "weight": 20}
            {"date": "2000-01-01", "weight": 10}
            """.trimIndent(),
        )
        val laptop = History.parse(
            """
            {"row_id": "s#0", "weight": 25.0, "reps": 8, "note": null}
            {"row_id": "s#1", "weight": 30, "reps": 12}
            {"row_id": "s#3", "weight": 15}
            {"date": "2000-01-01", "weight": 10}
            """.trimIndent(),
        )
        val report = History.compare(phone, laptop)
        assertEquals(JsonPrimitive(false), report["same"])
        assertEquals(JsonArray(listOf(JsonPrimitive("s#2"))), report["only_phone"])
        assertEquals(JsonArray(listOf(JsonPrimitive("s#3"))), report["only_laptop"])
        // 25 and 25.0 are the same; a missing note and a null one are the same.
        val differing = report["differing"] as JsonArray
        assertEquals(1, differing.size)
        assertEquals("{\"row_id\":\"s#1\",\"fields\":[\"reps\"]}", differing[0].toString())
        assertEquals(JsonPrimitive(1), report["unkeyed_phone"])
        assertTrue((History.compare(laptop, laptop)["same"] as JsonPrimitive).content.toBoolean())
        assertFalse((History.compare(phone.drop(3), phone)["same"] as JsonPrimitive).content.toBoolean())
    }
}
