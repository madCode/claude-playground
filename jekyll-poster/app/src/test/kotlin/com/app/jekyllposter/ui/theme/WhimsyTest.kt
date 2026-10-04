package com.app.jekyllposter.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhimsyTest {
    @Test fun everyTermGetsAColourEvenOneThatHashesToMinValue() {
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        assertTrue(termSlot("polygenelubricants", 5) in 0 until 5)
    }

    @Test fun aTermKeepsItsColourWhateverItsCase() {
        assertEquals(termSlot("Writing", 5), termSlot("writing", 5))
    }
}
