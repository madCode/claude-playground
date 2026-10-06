package com.app.jekyllposter.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchingTest {
    @Test fun aFailureIsAResult() {
        val result = catching { error("no") }
        assertTrue(result.isFailure)
        assertEquals("no", result.exceptionOrNull()!!.message)
    }

    @Test fun aCancellationGoesOnUp() {
        val thrown = runCatching { catching { throw CancellationException("gone") } }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test fun aTimeoutStillEndsTheWait() = runBlocking {
        // Caught as a failure, the timeout would let the block carry on after it.
        var after = false
        val result = withTimeoutOrNull(10) {
            catching { delay(1_000) }
            after = true
        }
        assertNull(result)
        assertEquals(false, after)
    }
}
