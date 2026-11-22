package spotter.app.log

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import spotter.coach.Journal
import spotter.coach.JournalEntry
import spotter.coach.LogDraft
import spotter.coach.Logging
import spotter.coach.Sheet
import java.time.LocalDate

/**
 * One row of the append-only log, as stored. Only inserted, never updated or deleted: a
 * correction or removal is another row (DESIGN.md, Rules). Lists are kept as JSON text.
 */
@Entity(tableName = "events")
data class EventRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val kind: String,
    val target: Long?,
    val date: String,
    val className: String,
    val row: String,
    val line: String,
    val key: String,
    val parts: String,
    val lb: Int,
    val count: Int?,
    val targetLow: Int?,
    val targetHigh: Int?,
    val ladder: String?,
    val reps: String,
    val hurt: Boolean,
    val formBroke: Boolean,
    val stopped: Boolean,
    val easy: Boolean,
    val hard: Boolean,
    val easier: Boolean,
    @ColumnInfo(defaultValue = "0") val heaviestOnly: Boolean = false,
    val shownLb: Int? = null,
    val shownCount: Int? = null,
    val roundLb: String? = null,
) {
    fun toEntry() = JournalEntry(
        id = id, at = at, kind = JournalEntry.Kind.valueOf(kind), target = target, date = LocalDate.parse(date),
        className = className, row = row, line = line, key = key, parts = Journal.partsFrom(parts), lb = lb,
        count = count, targetLow = targetLow, targetHigh = targetHigh, ladder = ladder?.let(Journal::intsFrom),
        reps = Journal.intsFrom(reps), hurt = hurt, formBroke = formBroke, stopped = stopped,
        easy = easy, hard = hard, easier = easier,
        heaviestOnly = heaviestOnly, shownLb = shownLb, shownCount = shownCount, roundLb = roundLb?.let(Journal::intsFrom),
    )

    companion object {
        fun of(e: JournalEntry) = EventRow(
            at = e.at, kind = e.kind.name, target = e.target, date = e.date.toString(), className = e.className,
            row = e.row, line = e.line, key = e.key, parts = Journal.partsJson(e.parts), lb = e.lb, count = e.count,
            targetLow = e.targetLow, targetHigh = e.targetHigh, ladder = e.ladder?.let(Journal::intsJson),
            reps = Journal.intsJson(e.reps), hurt = e.hurt, formBroke = e.formBroke, stopped = e.stopped,
            easy = e.easy, hard = e.hard, easier = e.easier,
            heaviestOnly = e.heaviestOnly, shownLb = e.shownLb, shownCount = e.shownCount, roundLb = e.roundLb?.let(Journal::intsJson),
        )
    }
}

@Dao
interface EventDao {
    // No update or delete here, on purpose: the log only grows.
    @Insert
    fun insert(row: EventRow): Long

    @Insert
    fun insertAll(rows: List<EventRow>)

    @Query("SELECT * FROM events ORDER BY id")
    fun all(): List<EventRow>
}

// A phone has had version 1 since the Log screen shipped, so every change to the table is a
// new version with a migration, checked against the schema kept in app/schemas.
@Database(
    entities = [EventRow::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = LogDatabase.OneWeightLadders::class), AutoMigration(from = 2, to = 3)],
)
abstract class LogDatabase : RoomDatabase() {
    abstract fun events(): EventDao

    /** Every ladder version 1 stored came from the Log screen's one weight, its heaviest round's. */
    class OneWeightLadders : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE events SET heaviestOnly = 1 WHERE ladder IS NOT NULL")
        }
    }

    companion object {
        @Volatile private var instance: LogDatabase? = null

        fun get(context: Context): LogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LogDatabase::class.java, "log.db").build().also { instance = it }
        }
    }
}

/** The log as the coach reads it. Call off the main thread. */
class EventLog(private val db: LogDatabase) {
    private val dao = db.events()

    fun write(entry: JournalEntry): Long = dao.insert(EventRow.of(entry))

    /**
     * Every entry, in the order written. A row that can't be read (damaged, or a kind from a
     * newer build after a downgrade) is skipped: it costs that one entry, not the whole log.
     */
    fun entries(): List<JournalEntry> = read().first

    /** The entries, and how many stored rows couldn't be read, for the export to own up to. */
    fun read(): Pair<List<JournalEntry>, Int> {
        val rows = dao.all()
        val entries = rows.mapNotNull { runCatching { it.toEntry() }.getOrNull() }
        return entries to rows.size - entries.size
    }

    /**
     * Saves a class's log screen: works out what changed against the log as stored now and writes
     * it in one transaction, so a reader never sees half a save and a killed app leaves none.
     */
    fun save(draft: LogDraft, sheet: Sheet, date: LocalDate, className: String, at: Long): List<JournalEntry> =
        db.runInTransaction<List<JournalEntry>> {
            val events = Logging.events(draft, sheet, Logging.logged(sheet, entries(), date, className), date, className, at)
            dao.insertAll(events.map(EventRow::of))
            events
        }
}
