package com.app.carry.brain

/**
 * Text handling ported from gym-coach (`wording.py`, and the Python string
 * methods the coach leans on). Each function here behaves as its Python
 * counterpart does, not as Kotlin's nearest one: Kotlin's `lines()` and
 * `trim()` split and trim on different characters, and Java's regex classes
 * are ASCII unless told otherwise, where Python's are Unicode. A difference
 * here would change which lines a board has and what they match.
 */

/** Python's `str.isspace()` for one character. */
internal fun Char.isPySpace(): Boolean =
    this in '\t'..'\r' || this in '\u001c'..'\u001f' || this == ' ' || this == '\u0085' ||
        this == ' ' || this == ' ' || this in ' '..' ' || this == ' ' ||
        this == ' ' || this == ' ' || this == ' ' || this == '　'

/** Python's `str.strip()`. */
internal fun String.pyStrip(): String = trim { it.isPySpace() }

/** Python's `str.splitlines()`: every line boundary Python knows, no trailing empty line. */
internal fun String.pySplitLines(): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '\n' || c == '\r' || c == '\u000b' || c == '\u000c' || c in '\u001c'..'\u001e' ||
            c == '\u0085' || c == ' ' || c == ' '
        ) {
            out += substring(start, i)
            if (c == '\r' && i + 1 < length && this[i + 1] == '\n') i++
            start = i + 1
        }
        i++
    }
    if (start < length) out += substring(start)
    return out
}

/** Python's `str.split()` with no argument: runs of whitespace, no empty pieces. */
internal fun String.pySplit(): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < length) {
        while (i < length && this[i].isPySpace()) i++
        val start = i
        while (i < length && !this[i].isPySpace()) i++
        if (i > start) out += substring(start, i)
    }
    return out
}

/**
 * A regex with Python 3's meaning for `\d`, `\s`, `\w` and `\b` in a `str`
 * pattern, which is Unicode. Java's are ASCII on the JVM and Unicode on
 * Android (ICU), and Android throws on the inline `(?U)` that would align
 * them, so each is spelled out as an explicit class that means the same on
 * both. Only outside a `[...]` class: none of the coach's patterns put one
 * inside, and [translate] refuses one there rather than guess.
 */
internal fun pyRegex(pattern: String, ignoreCase: Boolean = false): Regex =
    if (ignoreCase) Regex(translate(pattern), RegexOption.IGNORE_CASE) else Regex(translate(pattern))

private const val SPACE = "[\\t-\\r\\u001c-\\u001f \\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]"
private const val NOT_SPACE = "[^\\t-\\r\\u001c-\\u001f \\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]"
private const val WORD = "[\\p{L}\\p{N}_]"
private const val NOT_WORD = "[^\\p{L}\\p{N}_]"
private const val BOUNDARY = "(?:(?<=$WORD)(?!$WORD)|(?<!$WORD)(?=$WORD))"
private const val NOT_BOUNDARY = "(?:(?<=$WORD)(?=$WORD)|(?<!$WORD)(?!$WORD))"

private fun translate(pattern: String): String {
    val out = StringBuilder()
    var inClass = false
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\\' && i + 1 < pattern.length) {
            val n = pattern[i + 1]
            val swap = when (n) {
                'd' -> "\\p{Nd}"; 'D' -> "\\P{Nd}"
                's' -> SPACE; 'S' -> NOT_SPACE
                'w' -> WORD; 'W' -> NOT_WORD
                'b' -> BOUNDARY; 'B' -> NOT_BOUNDARY
                else -> null
            }
            require(swap == null || !inClass) { "\\$n inside a character class: $pattern" }
            out.append(swap ?: "$c$n")
            i += 2
            continue
        }
        if (c == '[' && !inClass) inClass = true else if (c == ']' && inClass) inClass = false
        out.append(c)
        i++
    }
    return out.toString()
}

/**
 * Python's `html.unescape`, for what the corpus uses: numeric references and
 * the entities a Reddit comment's markup escapes. An entity this does not
 * know is left as written, which is what Python does for an unknown one.
 */
internal fun htmlUnescape(s: String): String {
    if ('&' !in s) return s
    return ENTITY.replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)?.let(::codePointText)
            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointText)
            else -> NAMED[body]
        } ?: m.value
    }
}

private val ENTITY = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")
private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
)

private fun codePointText(cp: Int): String? =
    if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else "�"

// ------------------------------------------------------------ wording.py

/** A separator between digits becomes a space; see `wording._DIGIT_SEPARATOR_RE`. */
private val DIGIT_SEPARATOR = pyRegex("""(?<=\d)\s*[-–—/]\s*(?=\d)""")
private val NOT_A_WORD_CHAR = Regex("[^a-z0-9 ]")

/** `wording._normalize`: lower case, digit separators to spaces, then only a-z, 0-9 and spaces. */
internal fun normalize(text: String): String =
    NOT_A_WORD_CHAR.replace(DIGIT_SEPARATOR.replace(text.lowercase(), " "), "").pyStrip()

/** `wording.MAX_FILLER_WORDS`: filler words tolerated between an alias's words. */
internal const val MAX_FILLER_WORDS = 3

/** `wording._phrase_pattern`: [words] in order, up to [MAX_FILLER_WORDS] between each, each with an optional "s". */
internal fun phrasePattern(words: List<String>): Regex {
    val gap = """\s+(?:\S+\s+){0,$MAX_FILLER_WORDS}"""
    return pyRegex(words.joinToString(gap) { """\b${Regex.escape(it)}s?\b""" })
}
