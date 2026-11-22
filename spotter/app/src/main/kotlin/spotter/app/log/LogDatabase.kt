package spotter.app.log

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import spotter.coach.Journal
import spotter.coach.JournalEntry
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
    val ref: Int,
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
) {
    fun toEntry() = JournalEntry(
        id = id, at = at, kind = JournalEntry.Kind.valueOf(kind), target = target, date = LocalDate.parse(date),
        className = className, ref = ref, line = line, key = key, parts = Journal.partsFrom(parts), lb = lb,
        count = count, targetLow = targetLow, targetHigh = targetHigh, ladder = ladder?.let(Journal::intsFrom),
        reps = Journal.intsFrom(reps), hurt = hurt, formBroke = formBroke, stopped = stopped,
    )

    companion object {
        fun of(e: JournalEntry) = EventRow(
            at = e.at, kind = e.kind.name, target = e.target, date = e.date.toString(), className = e.className,
            ref = e.ref, line = e.line, key = e.key, parts = Journal.partsJson(e.parts), lb = e.lb, count = e.count,
            targetLow = e.targetLow, targetHigh = e.targetHigh, ladder = e.ladder?.let(Journal::intsJson),
            reps = Journal.intsJson(e.reps), hurt = e.hurt, formBroke = e.formBroke, stopped = e.stopped,
        )
    }
}

@Dao
interface EventDao {
    // No update or delete here, on purpose: the log only grows.
    @Insert
    fun insert(row: EventRow): Long

    @Query("SELECT * FROM events ORDER BY id")
    fun all(): List<EventRow>
}

@Database(entities = [EventRow::class], version = 1, exportSchema = true)
abstract class LogDatabase : RoomDatabase() {
    abstract fun events(): EventDao

    companion object {
        @Volatile private var instance: LogDatabase? = null

        fun get(context: Context): LogDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LogDatabase::class.java, "log.db").build().also { instance = it }
        }
    }
}

/** The log as the coach reads it. Call off the main thread. */
class EventLog(private val dao: EventDao) {
    fun write(entry: JournalEntry): Long = dao.insert(EventRow.of(entry))

    /**
     * Every entry, in the order written. A row that can't be read (damaged, or a kind from a
     * newer build after a downgrade) is skipped: it costs that one entry, not the whole log.
     */
    fun entries(): List<JournalEntry> = dao.all().mapNotNull { runCatching { it.toEntry() }.getOrNull() }
}
