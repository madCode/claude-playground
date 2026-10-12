package com.app.carry

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

/** Records which thread the folder was actually read on. */
private class ThreadRecordingFolder : Folder {
    var readOn: String? = null
    override val chosen = true
    override val grantLost = false
    override fun list(): List<Pair<String, String>> {
        readOn = Thread.currentThread().name
        return listOf("d" to "session-2026-10-17-2G.json")
    }
    override fun write(documentId: String, text: String) = false
    override fun read(documentId: String) =
        """{"schema":"gym-session/1","sheet_id":"2026-10-17:2G","date":"2026-10-17","class":"2G","sets":[]}"""
}

class SessionLoaderTest {

    @Test
    fun `the folder is read away from the caller's thread`() = runTest {
        // The point of the whole class. carryState is IPC to another app's
        // provider plus a file read; against a folder a sync is writing to
        // it can block for as long as it likes, and it used to run in
        // onResume -- an ANR at the one moment the app exists for.
        val folder = ThreadRecordingFolder()
        val io = Executors.newSingleThreadExecutor { r -> Thread(r, "carry-io-test") }
        try {
            val state = SessionLoader(folder, io.asCoroutineDispatcher()).load()
            assertTrue(state is CarryState.Ready)
            assertEquals("carry-io-test", folder.readOn)
            assertNotEquals(Thread.currentThread().name, folder.readOn)
        } finally {
            io.shutdown()
        }
    }
}
