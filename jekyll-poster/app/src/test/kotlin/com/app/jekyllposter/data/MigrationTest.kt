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
            db.query("SELECT title, extraFrontMatter FROM drafts WHERE id = 4").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Kept", c.getString(0))
                assertTrue(c.isNull(1))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
