package com.app.jekyllposter.core

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android compiles regular expressions with ICU, which refuses a `}` that doesn't close a `{n,m}`
 * repeat; the JVM these tests run on takes it. So a pattern can pass every test here and crash on
 * the phone, as the preview's did. This reads the app's own patterns for that one difference.
 */
class AndroidRegexTest {
    @Test fun noPatternHasABareClosingBrace() {
        val sources = listOf(File("src/main"), File("../app/src/main")).flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
        assertTrue("No sources found from ${File(".").absolutePath}", sources.size > 10)
        val bare = sources.flatMap { file ->
            literal.findAll(file.readText()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .filter { hasBareBrace(it) }.map { "${file.name}: $it" }.toList()
        }
        assertTrue("Escape these as \\}:\n" + bare.joinToString("\n"), bare.isEmpty())
    }

    private val literal = Regex("Regex\\(\"\"\"([\\s\\S]*?)\"\"\"|Regex\\(\"((?:[^\"\\\\]|\\\\.)+)\"")

    private fun hasBareBrace(pattern: String): Boolean = pattern
        .replace(Regex("\\$\\{[^}]*\\}"), "")         // Kotlin templates
        .replace(Regex("\\\\[pP]\\{[^}]*\\}"), "")       // Unicode properties like \p{L}
        .replace(Regex("\\\\\\\\|\\\\."), "")          // escaped characters
        .replace(Regex("\\[(?:\\\\.|[^\\]])*\\]"), "") // character classes
        .replace(Regex("\\{\\d*,?\\d*\\}"), "")        // repeats like {2} or {3,}
        .contains('}')
}
