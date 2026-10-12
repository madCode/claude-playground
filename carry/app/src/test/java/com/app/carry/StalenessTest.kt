package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StalenessTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun `a sheet from an earlier day is not today's`() {
        // Saturday 6am, the sync has not run, the newest file in the folder
        // is Thursday's. Showing those weights under "carry these in" is the
        // one way this app can put a wrong number in someone's head.
        assertEquals(SessionFiles.Day.PAST, SessionFiles.dayOf("2026-10-15", today))
    }

    @Test
    fun `today is today, and tomorrow is not`() {
        assertEquals(SessionFiles.Day.TODAY, SessionFiles.dayOf("2026-10-17", today))
        assertEquals(SessionFiles.Day.FUTURE, SessionFiles.dayOf("2026-10-18", today))
    }

    @Test
    fun `a date this app cannot read is admitted rather than assumed to be today`() {
        assertEquals(SessionFiles.Day.UNKNOWN, SessionFiles.dayOf("Fri 17 Oct", today))
        assertEquals(SessionFiles.Day.UNKNOWN, SessionFiles.dayOf("", today))
        assertEquals(SessionFiles.Day.UNKNOWN, SessionFiles.dayOf("2026-13-45", today))
    }

    @Test
    fun `dates are made readable without asserting anyone's language`() {
        // The formatter uses the reader's locale on purpose, so asserting
        // "Sat 17 Oct" would fail CI on a German runner. Assert the
        // properties instead: it stops being ISO, and it keeps the day.
        val shown = SessionFiles.formatDate("2026-10-17")
        assertNotEquals("2026-10-17", shown)
        assertTrue("the day of the month survives: $shown", shown.contains("17"))
        // Anything unparseable is shown as sent rather than hidden.
        assertEquals("whenever", SessionFiles.formatDate("whenever"))
    }
}
