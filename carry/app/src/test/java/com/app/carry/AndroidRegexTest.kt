package com.app.carry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android's regex is ICU's; the JVM's is not. Patterns that compile here can
 * throw on a phone, and the whole unit suite — Robolectric included — runs on
 * the JVM, so nothing in it notices.
 *
 * This is not hypothetical. `TypedBack` built `Regex("(?U)\\s+")`, which
 * OpenJDK accepts and ICU rejects outright. Every test passed. On a phone the
 * app died with PatternSyntaxException during the recomposition after a save —
 * after the file had been written, so the numbers survived and only the app
 * disappeared, which is the hardest kind of crash to tie back to its cause.
 */
class AndroidRegexTest {

    @Test
    fun spacesFoldsEveryKindOfSpaceIntoOne() {
        assertEquals("a b", "a  b".replace(SPACES, " "))
        assertEquals("a b", "a\tb".replace(SPACES, " "))
        assertEquals("a b", "a\nb".replace(SPACES, " "))
        // The ones (?U) was there for: a line separator and a non-breaking
        // space, which would otherwise break the sentence across lines.
        assertEquals("a b", "a b".replace(SPACES, " "))
        assertEquals("a b", "a b".replace(SPACES, " "))
        assertEquals("a b", "a b".replace(SPACES, " "))
        assertEquals("a b", "a     b".replace(SPACES, " "))
    }

    /**
     * The inline flags ICU has no answer for. A character class says the same
     * thing and compiles in both engines.
     */
    @Test
    fun noSourceUsesAnInlineFlagAndroidRejects() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("no sources found; this test is pointed at the wrong folder", sources.size > 10)
        // Inside a string literal only: prose about the flag, including the
        // comment that explains why it is gone, is not the flag.
        val inAPattern = Regex(""""[^"]*\(\?[A-Za-z]*U[A-Za-z]*\)""")
        val offenders = sources.mapNotNull { file ->
            val lines = file.readLines().withIndex().filter { (_, line) -> inAPattern.containsMatchIn(line) }
            if (lines.isEmpty()) null else "${file.name}: ${lines.map { it.index + 1 }}"
        }
        assertEquals("(?U) compiles on the JVM and throws on Android", emptyList<String>(), offenders)
    }
}
