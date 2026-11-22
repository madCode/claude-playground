package spotter.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import spotter.app.log.EventLog
import spotter.app.log.LogDatabase
import java.io.File

// A phone that logged with an older build keeps its log when the table gains columns: the
// database is made from each kept schema file, as that build made it, then opened by this one.
@RunWith(AndroidJUnit4::class)
class LogMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // The database as the build at [version] made it, with [insert] run on it.
    private fun made(version: Int, name: String, insert: (SQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(File("schemas/spotter.app.log.LogDatabase/$version.json").readText()).jsonObject["database"]!!.jsonObject
        val events = schema["entities"]!!.jsonArray.single().jsonObject
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(events["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", "events"))
            schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            insert(db)
            db.version = version
        }
    }

    // Version 2 knew which ladders had one weight; version 3 adds each round's, not said so far.
    @Test
    fun aLogFromVersion2IsKept() {
        made(2, "log-2.db") { db ->
            db.execSQL(
                """INSERT INTO events (at, kind, date, className, row, line, key, parts, lb, count, ladder, reps, hurt, formBroke, stopped, easy, hard, easier, heaviestOnly, shownLb, shownCount)
                   VALUES (1, 'LOGGED', '2026-03-01', '2G', 's', '10 / 8 / 6 x squat', 'squat', '[{"movement":"squat","variations":{}}]', 30, 2, '[10,8,6]', '[10,8,6]', 0, 0, 0, 0, 0, 0, 1, 25, 2)""",
            )
        }
        val db = Room.databaseBuilder(context, LogDatabase::class.java, "log-2.db").allowMainThreadQueries().build()
        try {
            val log = EventLog(db)
            val ladder = log.entries().single()
            assertEquals(listOf(30, true, 25), listOf(ladder.lb, ladder.heaviestOnly, ladder.shownLb))
            assertNull(ladder.roundLb)
            log.write(ladder.copy(id = 0, lb = 20, heaviestOnly = false, roundLb = listOf(20, 25, 30)))
            assertEquals(listOf(20, 25, 30), log.entries().last().roundLb)
        } finally {
            db.close()
        }
    }

    @Test
    fun aLogFromVersion1IsKept() {
        made(1, "old-log.db") { db ->
            // An invented row, as version 1 stored it.
            db.execSQL(
                """INSERT INTO events (at, kind, date, className, row, line, key, parts, lb, count, targetLow, targetHigh, reps, hurt, formBroke, stopped, easy, hard, easier)
                   VALUES (1, 'LOGGED', '2026-03-01', '2G', 'r', '8 x chest press', 'chest press', '[{"movement":"chest press","variations":{}}]', 25, 2, 8, 8, '[8]', 0, 0, 0, 0, 1, 0)""",
            )
            // A ladder, which version 1 could only have logged with one weight.
            db.execSQL(
                """INSERT INTO events (at, kind, date, className, row, line, key, parts, lb, count, ladder, reps, hurt, formBroke, stopped, easy, hard, easier)
                   VALUES (2, 'LOGGED', '2026-03-01', '2G', 's', '10 / 8 / 6 x squat', 'squat', '[{"movement":"squat","variations":{}}]', 30, 2, '[10,8,6]', '[10,8,6]', 0, 0, 0, 0, 0, 0)""",
            )
        }
        val db = Room.databaseBuilder(context, LogDatabase::class.java, "old-log.db").allowMainThreadQueries().build()
        try {
            val log = EventLog(db)
            val (entry, ladder) = log.entries()
            assertEquals(true, ladder.heaviestOnly)
            // And the migrated log still takes new entries.
            log.write(entry.copy(id = 0, shownLb = 25, shownCount = 2))
            assertEquals(25, log.entries().last().shownLb)
            assertEquals(listOf(25, 2), listOf(entry.lb, entry.count))
            assertEquals(true, entry.hard)
            // The new columns read as "not said".
            assertEquals(false, entry.heaviestOnly)
            assertNull(entry.shownLb)
        } finally {
            db.close()
        }
    }
}
