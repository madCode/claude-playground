package com.app.jekyllposter.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Where a post written on the phone stands. */
enum class PostState {
    /** Being written; only on the phone. */
    Draft,

    /** Waiting to go to GitHub, e.g. for a connection. */
    Queued,

    /** On GitHub. [Draft.buildState] says whether the site has it yet. */
    Published,

    /** Didn't go; [Draft.error] says why and the writer can try again. */
    Failed,
}

/** Whether GitHub Pages has built the commit that carried a post. */
enum class BuildState { Building, Live, Failed, Unknown }

/**
 * A post written or edited on the phone. It stays after publishing, so the writer can see it go
 * live; editing a published post again starts from the blog's copy, not this one.
 */
@Entity(tableName = "drafts")
data class Draft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** For an edit of a post already on the blog: its path, and its blob sha when it was opened. */
    val editingPath: String? = null,
    val baseSha: String? = null,
    val state: PostState = PostState.Draft,
    /**
     * Where the post is being written, fixed when it's queued, with its date. A retry after a
     * crash then writes the same file, and finds it already there instead of posting twice.
     */
    val targetPath: String? = null,
    val publishDate: String? = null,
    val commitSha: String? = null,
    val buildState: BuildState? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/** A post already on the blog, as last read, keyed by blob sha so unchanged posts aren't fetched again. */
@Entity(tableName = "posts")
data class CachedPost(
    @PrimaryKey val sha: String,
    val path: String,
    val title: String,
    val categories: List<String>,
    val tags: List<String>,
    val published: Boolean,
    val date: String?,
)

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun all(): Flow<List<Draft>>

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun get(id: Long): Draft?

    @Query("SELECT * FROM drafts")
    suspend fun list(): List<Draft>

    @Query("SELECT COUNT(*) FROM drafts")
    suspend fun snapshotCount(): Int

    @Query("SELECT * FROM drafts WHERE id = :id")
    fun watch(id: Long): Flow<Draft?>

    @Query("SELECT * FROM drafts WHERE editingPath = :path AND state != 'Published' LIMIT 1")
    suspend fun openEditOf(path: String): Draft?

    @Insert
    suspend fun insert(draft: Draft): Long

    @Update
    suspend fun update(draft: Draft)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM drafts")
    suspend fun clear()
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY date DESC, path DESC")
    fun all(): Flow<List<CachedPost>>

    @Query("SELECT * FROM posts")
    suspend fun snapshot(): List<CachedPost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<CachedPost>)

    /** Drops posts no longer on the blog. Paths too, since a renamed post keeps its sha. */
    @Query("DELETE FROM posts WHERE sha || ' ' || path NOT IN (:keep)")
    suspend fun keepOnly(keep: List<String>)

    @Query("DELETE FROM posts")
    suspend fun clear()
}

class Converters {
    @TypeConverter fun fromList(list: List<String>): String = Json.encodeToString(list)
    @TypeConverter fun toList(json: String): List<String> = Json.decodeFromString(json)
}

@Database(entities = [Draft::class, CachedPost::class], version = 1)
@TypeConverters(Converters::class)
abstract class PosterDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun posts(): PostDao
}
