package com.app.jekyllposter.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.jekyllposter.core.jekyll.extraFrontMatterProblem
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
 * [Delete] takes the post being edited off the blog. Stored by name, so adding one needs no
 * migration.
 */
enum class Destination { Posts, Drafts, Delete }

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
    /**
     * Front matter beyond the editor's fields, as YAML. For an edit, the post's other keys as
     * opened; null for a new post with none.
     */
    val extraFrontMatter: String? = null,
    /**
     * [extraFrontMatter] as the post had it when opened. Unchanged, it's written back byte for
     * byte, so it isn't checked: a post's own odd but working YAML mustn't block an edit.
     */
    val extraFrontMatterOpened: String? = null,
    /** Photos added on the phone, uploaded with the post if its text still links to them. */
    @ColumnInfo(defaultValue = "[]") val images: List<DraftImage> = emptyList(),
    /** For an edit of a post already on the blog: its path, and its blob sha when it was opened. */
    val editingPath: String? = null,
    val baseSha: String? = null,
    val state: PostState = PostState.Draft,
    @ColumnInfo(defaultValue = "Posts") val destination: Destination = Destination.Posts,
    /**
     * Where the post is being written, fixed when it's queued, with its date. A retry after a
     * crash then writes the same file, and finds it already there instead of posting twice.
     */
    val targetPath: String? = null,
    val publishDate: String? = null,
    val commitSha: String? = null,
    /**
     * The blob shas of every text a commit was attempted with for this post. If one landed
     * without the app hearing back, the file at [targetPath] has one of them: it's this post,
     * not someone else's.
     */
    @ColumnInfo(defaultValue = "[]") val sentShas: List<String> = emptyList(),
    /** Where the published post will be on the site. */
    val postUrl: String? = null,
    /**
     * The date the post is to carry: a shared note's `date:` as written, read when publishing in
     * the site's time zone as it is then. Null dates the post when it's published.
     */
    val noteDate: String? = null,
    /**
     * When a post sent at a random time may go out (epoch millis); null for at once. Chosen when
     * the writer taps Publish, so a restart or a retry doesn't choose again.
     */
    val sendAfter: Long? = null,
    val buildState: BuildState? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Why the "more front matter" can't be published, or null; only checked once it's changed. */
    val frontMatterProblem: String?
        get() = extraFrontMatter?.takeIf { it != extraFrontMatterOpened }?.let { extraFrontMatterProblem(it) }

    val isEmpty: Boolean get() = title.isBlank() && body.isBlank() && extraFrontMatter.isNullOrBlank() && images.isEmpty()

    /**
     * An edit of a blog post opened and never changed: nothing of the writer's to keep, and listing
     * it would show the post twice. Saves only happen on a change, so its times still agree.
     */
    val unchangedEdit: Boolean get() = editingPath != null && state == PostState.Draft && updatedAt == createdAt

    /** The writer can change it: published posts and ones on their way are read-only. */
    val editable: Boolean get() = state == PostState.Draft || state == PostState.Failed

    /** Queued, and nothing sent yet: the writer can take it back to edit ([DraftDao.takeBack]). */
    val canTakeBack: Boolean get() = state == PostState.Queued && destination != Destination.Delete && sentShas.isEmpty()

    /**
     * This row with the writer's text from [mine]: what the editor owns. The rest (publishing
     * state, names fixed for a commit) stays the stored row's, which the publisher changes.
     */
    fun withTextOf(mine: Draft): Draft = copy(
        title = mine.title, body = mine.body, categories = mine.categories, tags = mine.tags,
        images = mine.images, extraFrontMatter = mine.extraFrontMatter, noteDate = mine.noteDate,
    )
}

/** A photo added to a post: prepared on the phone, uploaded in the post's commit. */
@Serializable
data class DraftImage(
    /** Where it goes on the site, as the post links to it: `/assets/images/2026/….jpg`. */
    val sitePath: String,
    /** The prepared file in the app's storage. */
    val file: String,
)

/** A post already on a blog, as last read, keyed by blob sha so unchanged posts aren't fetched again. */
// By path, not sha: two posts with identical bytes share a blob sha.
@Entity(tableName = "posts", primaryKeys = ["blog", "path"])
data class CachedPost(
    /** The blog it's on ([Account.blogKey]). */
    val blog: String,
    val path: String,
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

    /**
     * Deletes the draft only if it's still an edit opened and never changed ([Draft.unchangedEdit]),
     * in one statement: an Update or Delete queued from it in the same moment must not be lost.
     */
    @Query("DELETE FROM drafts WHERE id = :id AND editingPath IS NOT NULL AND state = 'Draft' AND updatedAt = createdAt")
    suspend fun deleteIfUnchangedEdit(id: Long)

    /**
     * The phone's published records of the post now at [path] (where publishing put it, whether
     * written here or edited), e.g. once the post is deleted from the blog.
     */
    @Query("DELETE FROM drafts WHERE targetPath = :path AND blog = :blog AND state = 'Published'")
    suspend fun deletePublishedEditsOf(path: String, blog: String)

    @Insert
    suspend fun insert(draft: Draft): Long

    @Update
    suspend fun update(draft: Draft)

    /**
     * Takes a queued post back to a draft, if it's still queued, isn't a delete, and no commit was
     * tried for it ([Draft.canTakeBack]); the number of rows changed. A tried commit may have
     * landed unheard, and the post be live already.
     */
    @Query("UPDATE drafts SET state = 'Draft', sendAfter = NULL, updatedAt = :now WHERE id = :id AND state = 'Queued' AND destination != 'Delete' AND sentShas = '[]'")
    suspend fun takeBack(id: Long, now: Long = System.currentTimeMillis()): Int

    /** Clears a queued post's random time, so it goes now; the number of rows changed. */
    @Query("UPDATE drafts SET sendAfter = NULL WHERE id = :id AND state = 'Queued'")
    suspend fun sendNow(id: Long): Int

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM drafts")
    suspend fun clear()
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts WHERE blog = :blog ORDER BY date DESC, path DESC")
    fun all(blog: String): Flow<List<CachedPost>>

    @Query("SELECT * FROM posts WHERE blog = :blog")
    suspend fun snapshot(blog: String): List<CachedPost>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<CachedPost>)

    @Query("DELETE FROM posts WHERE blog = :blog AND path IN (:paths)")
    suspend fun deletePaths(blog: String, paths: List<String>)

    @Query("DELETE FROM posts WHERE blog IN (:blogs)")
    suspend fun clear(blogs: List<String>)
}

/** How a [SignIn] was made: a pasted token, or "Sign in with GitHub" (the GitHub App). */
enum class SignInKind { Token, GitHubApp }

/** A GitHub sign-in. Its tokens are sealed by [SecretCipher] and Base64-encoded. */
@Entity(tableName = "sign_ins")
data class SignIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val login: String,
    val kind: SignInKind,
    val sealedToken: String,
    val sealedRefreshToken: String? = null,
    /** When the token expires (epoch ms); null for one that doesn't. */
    val expiresAt: Long? = null,
)

/** A blog the app can post to, and the [SignIn] it posts with. */
@Entity(tableName = "blogs")
data class SavedBlog(
    /** [Account.blogKey]: `owner/repo@branch`. */
    @PrimaryKey val blogKey: String,
    val owner: String,
    val repo: String,
    val branch: String,
    val siteUrl: String? = null,
    val signInId: Long,
)

/** A blog with its sign-in. */
data class BlogWithSignIn(
    @Embedded val blog: SavedBlog,
    @Relation(parentColumn = "signInId", entityColumn = "id") val signIn: SignIn?,
)

/** The sign-ins and blogs behind [AccountStore]. */
@Dao
abstract class AccountDao {
    @Transaction
    @Query("SELECT * FROM blogs WHERE blogKey = :key")
    abstract suspend fun get(key: String): BlogWithSignIn?

    @Transaction
    @Query("SELECT * FROM blogs WHERE blogKey = :key")
    abstract fun watch(key: String): Flow<BlogWithSignIn?>

    @Query("SELECT * FROM blogs WHERE blogKey = :key")
    abstract suspend fun blog(key: String): SavedBlog?

    @Query("SELECT * FROM sign_ins WHERE id = :id")
    abstract suspend fun signIn(id: Long): SignIn?

    @Insert
    abstract suspend fun insert(signIn: SignIn): Long

    /** The number of rows changed: none when the sign-in was removed meanwhile. */
    @Update
    abstract suspend fun update(signIn: SignIn): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(blog: SavedBlog)

    @Query("SELECT blogKey FROM blogs WHERE signInId = :signInId")
    abstract suspend fun blogsOf(signInId: Long): List<String>

    @Query("DELETE FROM blogs WHERE signInId = :signInId")
    abstract suspend fun deleteBlogsOf(signInId: Long)

    @Query("DELETE FROM sign_ins WHERE id = :id")
    abstract suspend fun deleteSignIn(id: Long)

    /** Sign-ins no blog uses any more, e.g. one replaced by signing in to its blog afresh. */
    @Query("DELETE FROM sign_ins WHERE id NOT IN (SELECT signInId FROM blogs)")
    abstract suspend fun deleteUnused()

    /** Drafts saved before they had a blog, given [key]'s. */
    @Query("UPDATE drafts SET blog = :key WHERE blog IS NULL")
    abstract suspend fun adoptDrafts(key: String)

    /** [signIn] posting to [blog], replacing any sign-in [blog] had; the sign-in's id. */
    @Transaction
    open suspend fun signInTo(signIn: SignIn, blog: SavedBlog): Long {
        val id = insert(signIn)
        upsert(blog.copy(signInId = id))
        deleteUnused()
        return id
    }

    /**
     * The account from before sign-ins had a table: its sign-in and blog, written once however
     * often this runs (it's run again if the app was stopped before the old record was cleared),
     * and the drafts with no blog given it.
     */
    @Transaction
    open suspend fun importAccount(signIn: SignIn, blog: SavedBlog) {
        val existing = blog(blog.blogKey)?.signInId?.takeIf { signIn(it) != null }
        val id = if (existing != null) existing.also { update(signIn.copy(id = it)) } else insert(signIn)
        upsert(blog.copy(signInId = id))
        adoptDrafts(blog.blogKey)
    }

    /** Removes [signInId] and its blogs; the keys of the blogs removed. */
    @Transaction
    open suspend fun remove(signInId: Long): List<String> {
        val keys = blogsOf(signInId)
        deleteBlogsOf(signInId)
        deleteSignIn(signInId)
        return keys
    }
}

class Converters {
    @TypeConverter fun fromDestination(d: Destination): String = d.name
    @TypeConverter fun toDestination(s: String): Destination = Destination.valueOf(s)
    @TypeConverter fun fromKind(k: SignInKind): String = k.name
    @TypeConverter fun toKind(s: String): SignInKind = SignInKind.valueOf(s)
    @TypeConverter fun fromList(list: List<String>): String = Json.encodeToString(list)
    @TypeConverter fun toList(json: String): List<String> = Json.decodeFromString(json)
    @TypeConverter fun fromImages(list: List<DraftImage>): String = Json.encodeToString(list)
    @TypeConverter fun toImages(json: String): List<DraftImage> = Json.decodeFromString(json)
}

@Database(entities = [Draft::class, CachedPost::class, SignIn::class, SavedBlog::class], version = 6)
@TypeConverters(Converters::class)
abstract class PosterDatabase : RoomDatabase() {
    abstract fun drafts(): DraftDao
    abstract fun posts(): PostDao
    abstract fun accounts(): AccountDao

    companion object {
        /** The app's database, brought up to date from any version a debug build has shipped. */
        fun create(context: Context): PosterDatabase = Room.databaseBuilder(context, PosterDatabase::class.java, "poster.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
    }
}

/**
 * Version 1 shipped with cycle 1's debug build. Drafts gain their blog, photos, address, where
 * they go and the sha last sent; the post cache is keyed by path now, so it's simply rebuilt:
 * the next read of the blog fills it again.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drafts ADD COLUMN blog TEXT")
        db.execSQL("ALTER TABLE drafts ADD COLUMN images TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE drafts ADD COLUMN destination TEXT NOT NULL DEFAULT 'Posts'")
        db.execSQL("ALTER TABLE drafts ADD COLUMN postUrl TEXT")
        db.execSQL("ALTER TABLE drafts ADD COLUMN sentShas TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("DROP TABLE posts")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `posts` (`path` TEXT NOT NULL, `sha` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`categories` TEXT NOT NULL, `tags` TEXT NOT NULL, `published` INTEGER NOT NULL, `date` TEXT, PRIMARY KEY(`path`))",
        )
    }
}

/** Drafts gain their "more front matter", and what it was when the post was opened. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drafts ADD COLUMN extraFrontMatter TEXT")
        db.execSQL("ALTER TABLE drafts ADD COLUMN extraFrontMatterOpened TEXT")
    }
}

/** Drafts gain the date a shared note asked for. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drafts ADD COLUMN noteDate TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE drafts ADD COLUMN sendAfter INTEGER")
    }
}

/**
 * Sign-ins and blogs get tables of their own (the account still in DataStore is moved into them
 * on first use, by [AccountStore]), and the post cache a blog column. The cache is rebuilt: the
 * next read of the blog fills it again.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sign_ins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `login` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `sealedToken` TEXT NOT NULL, `sealedRefreshToken` TEXT, `expiresAt` INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `blogs` (`blogKey` TEXT NOT NULL, `owner` TEXT NOT NULL, `repo` TEXT NOT NULL, " +
                "`branch` TEXT NOT NULL, `siteUrl` TEXT, `signInId` INTEGER NOT NULL, PRIMARY KEY(`blogKey`))",
        )
        db.execSQL("DROP TABLE posts")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `posts` (`blog` TEXT NOT NULL, `path` TEXT NOT NULL, `sha` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`categories` TEXT NOT NULL, `tags` TEXT NOT NULL, `published` INTEGER NOT NULL, `date` TEXT, PRIMARY KEY(`blog`, `path`))",
        )
    }
}
