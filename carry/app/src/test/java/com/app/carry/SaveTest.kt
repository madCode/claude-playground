package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Test

/** A folder whose writes can be made to misbehave the way a real provider
 *  does: refuse, or truncate and then fail part way through. */
private class FlakyFolder(
    start: String,
    private val behaviour: (attempt: Int) -> Write,
) : Folder {
    enum class Write { OK, REFUSE, TRUNCATE_THEN_FAIL }

    var content: String? = start
    var attempts = 0
    override val chosen = true
    override val grantLost = false
    override fun list() = listOf("d" to "session-2026-10-17-2G.json")
    override fun read(documentId: String) = content
    override fun write(documentId: String, text: String): Boolean {
        attempts++
        return when (behaviour(attempts)) {
            Write.OK -> { content = text; true }
            Write.REFUSE -> false
            // What "wt" really does: the old content is gone the moment the
            // stream opens, whatever happens next.
            Write.TRUNCATE_THEN_FAIL -> { content = ""; false }
        }
    }
}

class SaveTest {
    private val original = """{"schema":"gym-session/1","sets":[]}"""
    private val updated = """{"schema":"gym-session/1","sets":[1]}"""

    @Test
    fun `a clean write reports success and leaves the new text`() {
        val folder = FlakyFolder(original) { FlakyFolder.Write.OK }
        assertEquals(Saved.YES, folder.save("d", updated))
        assertEquals(updated, folder.content)
    }

    @Test
    fun `a refused write leaves the file exactly as it was`() {
        val folder = FlakyFolder(original) { FlakyFolder.Write.REFUSE }
        assertEquals(Saved.NO_FILE_INTACT, folder.save("d", updated))
        assertEquals(original, folder.content)
    }

    @Test
    fun `a write that truncates and then fails is put back`() {
        // The dangerous one. Opening for writing destroys the old content
        // before a single byte of the new is written, so a failure here
        // takes the owner's numbers and the runner's intel with it.
        var attempt = 0
        val folder = FlakyFolder(original) {
            attempt++
            if (attempt == 1) FlakyFolder.Write.TRUNCATE_THEN_FAIL else FlakyFolder.Write.OK
        }
        assertEquals(Saved.NO_FILE_INTACT, folder.save("d", updated))
        assertEquals("the original must be back", original, folder.content)
    }

    @Test
    fun `a file that cannot be put back is reported as damaged, not as a retry`() {
        // "Try again" and "your file needs rescuing" are different things to
        // tell someone, and only one of them is honest here.
        val folder = FlakyFolder(original) { FlakyFolder.Write.TRUNCATE_THEN_FAIL }
        assertEquals(Saved.NO_FILE_DAMAGED, folder.save("d", updated))
    }

    @Test
    fun `a write that claims success but changed nothing is caught, and the file is fine`() {
        val folder = object : Folder {
            var content = "something else entirely"
            override val chosen = true
            override val grantLost = false
            override fun list() = emptyList<Pair<String, String>>()
            override fun read(documentId: String) = content
            override fun write(documentId: String, text: String) = true   // lies
        }
        // Not DAMAGED: nothing was written, so nothing was lost. Saying
        // "your file needs rescuing" here would be its own wrong answer.
        assertEquals(Saved.NO_FILE_INTACT, folder.save("d", updated))
    }
}
