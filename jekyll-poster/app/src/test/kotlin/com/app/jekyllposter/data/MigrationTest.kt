package com.app.jekyllposter.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.app.jekyllposter.testutil.TestApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Cycle 1's debug build is installed and in use, so schema changes carry its drafts forward. */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApp::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PosterDatabase::class.java)

    @Test fun version1DraftsSurviveAndThePostCacheIsRebuilt() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO drafts (id, title, body, categories, tags, state, createdAt, updatedAt) " +
                    "VALUES (3, 'Half written', 'Some words', '[\"writing\"]', '[]', 'Draft', 0, 0)",
            )
            db.execSQL("INSERT INTO posts (sha, path, title, categories, tags, published) VALUES ('abc', '_posts/2025-01-01-a.md', 'A', '[]', '[]', 1)")
        }
        helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT title, body, images, destination, blog FROM drafts WHERE id = 3").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Half written", c.getString(0))
                assertEquals("Some words", c.getString(1))
                assertEquals("[]", c.getString(2))
                assertEquals("Posts", c.getString(3))
                assertTrue(c.isNull(4))
            }
            db.query("SELECT COUNT(*) FROM posts").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun version2DraftsGainMoreFrontMatter() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL("INSERT INTO drafts (id, title, body, categories, tags, state, createdAt, updatedAt) VALUES (4, 'Kept', 'b', '[]', '[]', 'Draft', 0, 0)")
        }
        helper.runMigrationsAndValidate(DB, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT title, extraFrontMatter, extraFrontMatterOpened FROM drafts WHERE id = 4").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Kept", c.getString(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    @Test fun version3DraftsGainANoteDate() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL("INSERT INTO drafts (id, title, body, categories, tags, state, createdAt, updatedAt) VALUES (5, 'Kept', 'b', '[]', '[]', 'Draft', 0, 0)")
        }
        helper.runMigrationsAndValidate(DB, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT title, noteDate FROM drafts WHERE id = 5").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Kept", c.getString(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    @Test fun version4DraftsGainASendTime() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL("INSERT INTO drafts (id, title, body, categories, tags, state, createdAt, updatedAt) VALUES (6, 'Kept', 'b', '[]', '[]', 'Queued', 0, 0)")
        }
        helper.runMigrationsAndValidate(DB, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT title, sendAfter FROM drafts WHERE id = 6").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Kept", c.getString(0))
                // Queued before the switch existed: goes out at once.
                assertTrue(c.isNull(1))
            }
        }
    }

    @Test fun version5DraftsStayAndThePostCacheIsRebuiltPerBlog() {
        helper.createDatabase(DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO drafts (id, blog, title, body, categories, tags, state, sentShas, sendAfter, createdAt, updatedAt) " +
                    "VALUES (7, 'writer/blog@main', 'Waiting', 'b', '[]', '[]', 'Queued', '[]', 99, 0, 0)",
            )
            db.execSQL("INSERT INTO drafts (id, title, body, categories, tags, state, error, createdAt, updatedAt) VALUES (8, 'Failed', 'b', '[]', '[]', 'Failed', 'No', 0, 0)")
            db.execSQL("INSERT INTO posts (path, sha, title, categories, tags, published) VALUES ('_posts/2025-01-01-a.md', 'abc', 'A', '[]', '[]', 1)")
        }
        helper.runMigrationsAndValidate(DB, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT blog, state, sendAfter FROM drafts WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("writer/blog@main", c.getString(0))
                assertEquals("Queued", c.getString(1))
                assertEquals(99, c.getLong(2))
            }
            db.query("SELECT state, error FROM drafts WHERE id = 8").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Failed", c.getString(0))
                assertEquals("No", c.getString(1))
            }
            // A cache with no blog to say whose: the next read fills it again.
            db.query("SELECT COUNT(*) FROM posts").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            db.query("SELECT COUNT(*) FROM sign_ins").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun version6BlogsKeepTheirSignInAndGainATitle() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken) VALUES (3, 'writer', 'Token', 'sealed')")
            db.execSQL("INSERT INTO blogs (blogKey, owner, repo, branch, siteUrl, signInId) VALUES ('writer/blog@main', 'writer', 'blog', 'main', 'https://writer.github.io/blog/', 3)")
        }
        helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT signInId, siteUrl, title FROM blogs WHERE blogKey = 'writer/blog@main'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3, c.getLong(0))
                assertEquals("https://writer.github.io/blog/", c.getString(1))
                // Unknown until the blog is next read.
                assertTrue(c.isNull(2))
            }
        }
    }

    @Test fun version7BlogsGainATableOfSwitches() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL("INSERT INTO blogs (blogKey, owner, repo, branch, signInId, title) VALUES ('writer/blog@main', 'writer', 'blog', 'main', 3, 'A Notebook')")
        }
        helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT title FROM blogs WHERE blogKey = 'writer/blog@main'").use { c -> assertTrue(c.moveToFirst()); assertEquals("A Notebook", c.getString(0)) }
            // Empty: the switches kept for the whole app are moved in on first use.
            db.query("SELECT COUNT(*) FROM blog_privacy").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun version8AccountsSignedInWithGitHubTwiceKeepOneSignIn() {
        helper.createDatabase(DB, 8).use { db ->
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken, expiresAt) VALUES (1, 'writer', 'GitHubApp', 'renewed-last', 5000)")
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken) VALUES (2, 'other', 'GitHubApp', 'other')")
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken, expiresAt) VALUES (3, 'Writer', 'GitHubApp', 'made-last', 4000)")
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken) VALUES (4, 'writer', 'Token', 'pasted')")
            db.execSQL("INSERT INTO sign_ins (id, login, kind, sealedToken) VALUES (5, 'writer', 'Token', 'pasted-too')")
            listOf("a" to 1, "b" to 3, "c" to 2, "d" to 4, "e" to 5).forEach { (repo, id) ->
                db.execSQL("INSERT INTO blogs (blogKey, owner, repo, branch, signInId) VALUES ('writer/$repo@main', 'writer', '$repo', 'main', $id)")
            }
        }
        helper.runMigrationsAndValidate(DB, 9, true, MIGRATION_8_9).use { db ->
            db.query("SELECT id FROM sign_ins ORDER BY id").use { c ->
                assertEquals(listOf(1L, 2L, 4L, 5L), generateSequence { if (c.moveToNext()) c.getLong(0) else null }.toList())
            }
            db.query("SELECT repo, signInId FROM blogs ORDER BY repo").use { c ->
                val rows = generateSequence { if (c.moveToNext()) c.getString(0) to c.getLong(1) else null }.toList()
                // The sign-in renewed last takes the other's blog; pasted tokens stay apart.
                assertEquals(listOf("a" to 1L, "b" to 1L, "c" to 2L, "d" to 4L, "e" to 5L), rows)
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
