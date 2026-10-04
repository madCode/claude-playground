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
import kotlinx.serialization.Serializable
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

/**
 * Where publishing puts a post: `_posts`, live on the site, or the blog's `_drafts`, which
 * Jekyll doesn't publish, for finishing on a laptop. Publishing a Jekyll draft to [Posts] moves it.
 */
enum class Destination { Posts, Drafts }

/** Whether GitHub Pages has built the commit that carried a post. */
enum class BuildState { Building, Live, Failed, Unknown }

/**
 * A post written or edited on the phone. It stays after publishing, so the writer can see it go
 * live; editing a published post again starts from the blog's copy, not this one.
 */
@Entity(tableName = "drafts")
data class Draft(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The blog it's written for ([Account.blogKey]); null until it's first saved for one. */
    val blog: String? = null,
    val title: String = "",
    val body: String = "",
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Photos added on the phone, uploaded with the post if its text still links to them. */
    val images: List<DraftImage> = emptyList(),
    /** For an edit of a post already on the blog: its path, and its blob sha when it was opened. */
    val editingPath: String? = null,
    val baseSha: String? = null,
    val state: PostState = PostState.Draft,
    val destination: Destination = Destination.Posts,
    /**
     * Where the post is being written, fixed when it's queued, with its date. A retry after a
     * crash then writes the same file, and finds it already there instead of posting twice.
     */
    val targetPath: String? = null,
    val publishDate: String? = null,
    val commitSha: String? = null,
    /** Where the published post will be on the site. */
    val postUrl: String? = null,
    val buildState: BuildState? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()
}

/** A photo added to a post: prepared on the phone, uploaded in the post's commit. */
@Serializable
data class DraftImage(
    /** Where it goes on the site, as the post links to it: `/assets/images/2026/….jpg`. */
    val sitePath: String,
    /** The prepared file in the app's storage. */
    val file: String,
)

/** A post already on the blog, as last read, keyed by blob sha so unchanged posts aren't fetched again. */
@Entity(tableName = "posts")
data class CachedPost(
    // By path: two posts with identical bytes share a blob sha.
    @PrimaryKey val path: String,
    val sha: String,
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

    @Query("SELECT * FROM drafts WHERE editingPath = :path AND blog = :blog AND state != 'Published' LIMIT 1")
    suspend fun openEditOf(path: String, blog: String): Draft?

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

    @Query("DELETE FROM posts WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM posts")
    suspend fun clear()
}

class Converters {
    @TypeConverter fun fromDestination(d: Destination): String = d.name
    @TypeConverter fun toDestination(s: String): Destination = Destination.valueOf(s)
    @TypeConverter fun fromList(list: List<String>): String = Json.encodeToString(list)
    @TypeConverter fun toList(json: String): List<String> = Json.decodeFromString(json)
    @TypeConverter fun fromImages(list: List<DraftImage>): String = Json.encodeToString(list)
    @TypeConverter fun toImages(json: String): List<DraftImage> = Json.decodeFromString(json)
}

@Database(entities = [Draft::class, CachedPost::class], version = 1)
@TypeConverters(Converters::class)
abstract class PosterDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun posts(): PostDao
}
