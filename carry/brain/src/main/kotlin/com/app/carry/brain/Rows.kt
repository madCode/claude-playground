package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.boolean
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * History rows are JSON objects, as gym-coach keeps them: the coach reads
 * them with `dict.get` and Python's truthiness, and compares two rows by
 * value (`h in usable`), which a [JsonObject] also does. These read a row
 * the way that Python does.
 */
internal typealias Row = JsonObject

/** `row.get(key)`, with a stored null read as absent. */
internal fun JsonObject.el(key: String): JsonElement? = this[key]?.takeIf { it !is JsonNull }

internal fun JsonObject.str(key: String): String? = (el(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A number, or a bool as Python's 1 and 0. */
internal fun JsonObject.num(key: String): Double? = (el(key) as? JsonPrimitive)?.takeIf { !it.isString }
    ?.let { p -> p.booleanOrNull?.let { if (it) 1.0 else 0.0 } ?: p.doubleOrNull }

/** A whole number; a float with no fraction reads as one, as Python's arithmetic would use it. */
internal fun JsonObject.int(key: String): Int? = num(key)?.takeIf { it == Math.floor(it) && !it.isInfinite() }?.toInt()

/** `isinstance(row.get(key), int)`: written as an integer, which `json.dumps` keeps distinct from a float. */
internal fun JsonObject.isInt(key: String): Boolean =
    (el(key) as? JsonPrimitive)?.let { !it.isString && it.booleanOrNull == null && INTEGER.matches(it.content) } == true

private val INTEGER = Regex("-?[0-9]+")

internal fun JsonObject.strings(key: String): List<String>? =
    (el(key) as? JsonArray)?.map { (it as JsonPrimitive).content }

internal fun JsonObject.obj(key: String): JsonObject? = el(key) as? JsonObject

/** Python's `bool(row.get(key))`. */
internal fun JsonObject.flag(key: String): Boolean = truthy(this[key])

internal fun truthy(e: JsonElement?): Boolean = when (e) {
    null, JsonNull -> false
    is JsonPrimitive -> if (e.isString) e.content.isNotEmpty() else e.booleanOrNull ?: (e.doubleOrNull != 0.0)
    is JsonArray -> e.isNotEmpty()
    is JsonObject -> e.isNotEmpty()
}

/** `{**row, key: value, ...}`. */
internal fun JsonObject.with(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(this + pairs)

internal fun json(s: String?): JsonElement = s?.let(::JsonPrimitive) ?: JsonNull
internal fun json(n: Number?): JsonElement = n?.let(::JsonPrimitive) ?: JsonNull
internal fun json(b: Boolean): JsonElement = JsonPrimitive(b)
internal fun json(xs: List<String>?): JsonElement = xs?.let { JsonArray(it.map(::JsonPrimitive)) } ?: JsonNull

/**
 * Python's `f"{x:g}"`: six significant digits, trailing zeros dropped, so
 * 30.0 prints "30" and 12.5 "12.5". Rounding is half-even on the exact
 * binary value, as Python's is.
 */
internal fun g(x: Double): String {
    if (x == 0.0) return if (1 / x < 0) "-0" else "0"
    if (x.isNaN()) return "nan"
    if (x.isInfinite()) return if (x > 0) "inf" else "-inf"
    val rounded = BigDecimal(x).round(MathContext(6, RoundingMode.HALF_EVEN))
    val exp = rounded.precision() - rounded.scale() - 1
    if (exp in -4 until 6) return rounded.stripTrailingZeros().toPlainString()
    val digits = rounded.unscaledValue().abs().toString().trimEnd('0').ifEmpty { "0" }
    val mantissa = if (digits.length > 1) "${digits[0]}.${digits.substring(1)}" else digits
    val sign = if (rounded.signum() < 0) "-" else ""
    return "$sign${mantissa}e${if (exp < 0) "-" else "+"}${"%02d".format(Math.abs(exp))}"
}

internal fun date(iso: String): LocalDate = LocalDate.parse(iso)

/** `(later - earlier).days`. */
internal fun daysBetween(earlier: LocalDate, later: LocalDate): Long = ChronoUnit.DAYS.between(earlier, later)

/** `gym_lib._date_label`: the weekday within the last week, else M/D. */
internal fun dateLabel(iso: String, today: LocalDate): String {
    val d = date(iso)
    val delta = daysBetween(d, today)
    if (delta in 0..6) return d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    return "${d.monthValue}/${d.dayOfMonth}"
}

// ------------------------------------------------- Python's view of JSON values

/**
 * A Python exception the port raises where gym-coach's Python would, named
 * as Python names it, because the importer's report prints that name.
 * [valueError] when Python's is a `ValueError`, which the importer catches
 * as an unreadable file rather than an unexpected failure.
 */
internal class PyException(val pyName: String, val valueError: Boolean, message: String) : Exception("$pyName: $message")

/** What Python's `json.loads` types a non-string scalar as. */
private sealed interface PyNum {
    data class Int(val v: BigInteger) : PyNum
    data class Float(val v: Double) : PyNum
}

/** A bool, int or float as Python holds it (a bool is the int 1 or 0), or null for a string, null or container. */
private fun pyNum(e: JsonElement?): PyNum? {
    if (e !is JsonPrimitive || e is JsonNull || e.isString) return null
    return when (e.content) {
        "true" -> PyNum.Int(BigInteger.ONE)
        "false" -> PyNum.Int(BigInteger.ZERO)
        else -> if (INTEGER.matches(e.content)) PyNum.Int(BigInteger(e.content)) else PyNum.Float(e.content.toDouble())
    }
}

internal fun isPyStr(e: JsonElement?) = e is JsonPrimitive && e.isString
internal fun isPyBool(e: JsonElement?) = e is JsonPrimitive && e !is JsonNull && !e.isString && e.booleanOrNull != null

/** `isinstance(e, int) and not isinstance(e, bool)`. */
internal fun pyIntOf(e: JsonElement?): BigInteger? =
    (pyNum(e) as? PyNum.Int)?.v?.takeUnless { isPyBool(e) }

/** `isinstance(e, float)`. */
internal fun pyFloatOf(e: JsonElement?): Double? = (pyNum(e) as? PyNum.Float)?.v

/** Python's `==` on two loaded JSON values: 25 equals 25.0, True equals 1, NaN equals nothing. */
internal fun pyEquals(a: JsonElement?, b: JsonElement?): Boolean {
    val x = a ?: JsonNull
    val y = b ?: JsonNull
    if (x is JsonNull || y is JsonNull) return x is JsonNull && y is JsonNull
    if (x is JsonArray) return y is JsonArray && x.size == y.size && x.indices.all { pyEquals(x[it], y[it]) }
    if (x is JsonObject) return y is JsonObject && x.keys == y.keys && x.all { (k, v) -> pyEquals(v, y[k]) }
    if (y !is JsonPrimitive) return false
    x as JsonPrimitive
    if (x.isString || y.isString) return x.isString && y.isString && x.content == y.content
    val p = pyNum(x)!!
    val q = pyNum(y)!!
    return when {
        p is PyNum.Int && q is PyNum.Int -> p.v == q.v
        p is PyNum.Float && q is PyNum.Float -> p.v == q.v
        else -> {
            // An int against a float compares exactly, as Python does: 2**53 + 1 is not 2.0**53.
            val f = (if (p is PyNum.Float) p else q as PyNum.Float).v
            val n = (if (p is PyNum.Int) p else q as PyNum.Int).v
            f.isFinite() && BigDecimal(f).compareTo(BigDecimal(n)) == 0
        }
    }
}

/** Python's `str(e)`: a string as it is, anything else as its [pyRepr]. */
internal fun pyStrOf(e: JsonElement?): String = if (isPyStr(e)) (e as JsonPrimitive).content else pyRepr(e)

/**
 * Python's `repr()` of a loaded JSON value, as an f-string's `{v!r}` writes
 * it into a message: `None`, `True`, `30`, `30.0`, `'text'`, `[1, 'a']`,
 * `{'k': None}`.
 */
internal fun pyRepr(e: JsonElement?): String = when {
    e == null || e is JsonNull -> "None"
    e is JsonArray -> e.joinToString(", ", "[", "]") { pyRepr(it) }
    e is JsonObject -> e.entries.joinToString(", ", "{", "}") { (k, v) -> "${pyStrRepr(k)}: ${pyRepr(v)}" }
    isPyStr(e) -> pyStrRepr((e as JsonPrimitive).content)
    isPyBool(e) -> if ((e as JsonPrimitive).boolean) "True" else "False"
    else -> when (val n = pyNum(e)!!) {
        is PyNum.Int -> n.v.toString()
        is PyNum.Float -> pyFloatRepr(n.v)
    }
}

/**
 * Python's `repr()` of a str: single quotes unless the text has one and no
 * double quote; backslash escapes for the quote, `\\`, tab, newline and
 * return; `\xNN`, `\uNNNN` or `\UNNNNNNNN` for what Python's
 * `str.isprintable()` refuses. Printability follows this JVM's Unicode
 * tables, which can lag or lead Python's by a version on new characters.
 */
internal fun pyStrRepr(s: String): String {
    val quote = if ('\'' in s && '"' !in s) '"' else '\''
    val out = StringBuilder().append(quote)
    var i = 0
    while (i < s.length) {
        val cp = s.codePointAt(i)
        i += Character.charCount(cp)
        when {
            cp == quote.code || cp == '\\'.code -> out.append('\\').appendCodePoint(cp)
            cp == '\t'.code -> out.append("\\t")
            cp == '\n'.code -> out.append("\\n")
            cp == '\r'.code -> out.append("\\r")
            cp < 0x20 || cp == 0x7f -> out.append("\\x%02x".format(cp))
            cp < 0x7f || pyPrintable(cp) -> out.appendCodePoint(cp)
            cp <= 0xff -> out.append("\\x%02x".format(cp))
            cp <= 0xffff -> out.append("\\u%04x".format(cp))
            else -> out.append("\\U%08x".format(cp))
        }
    }
    return out.append(quote).toString()
}

/** `str.isprintable()` for a character past ASCII: not a control, format, surrogate, private, unassigned or separator. */
private fun pyPrintable(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
    Character.CONTROL, Character.FORMAT, Character.SURROGATE, Character.PRIVATE_USE, Character.UNASSIGNED,
    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SPACE_SEPARATOR -> false
    else -> true
}

/**
 * Python's `repr()` of a float: the shortest digits that read back as the
 * same double (the closest such when two are as short), fixed-point from
 * 1e-4 up to 1e16 with ".0" on a whole number, scientific outside it.
 */
internal fun pyFloatRepr(x: Double): String {
    if (x.isNaN()) return "nan"
    if (x.isInfinite()) return if (x > 0) "inf" else "-inf"
    if (x == 0.0) return if (1 / x < 0) "-0.0" else "0.0"
    val exact = BigDecimal(x)
    // At each length, only the nearest decimal below and above can read back as x.
    val digits = (1..17).firstNotNullOf { p ->
        listOf(RoundingMode.HALF_EVEN, RoundingMode.FLOOR, RoundingMode.CEILING)
            .map { exact.round(MathContext(p, it)) }
            .filter { it.toString().toDouble() == x }
            .minByOrNull { it.subtract(exact).abs() }
    }.stripTrailingZeros()
    val exp = digits.precision() - digits.scale() - 1
    if (exp in -4 until 16) {
        val plain = digits.toPlainString()
        return if ('.' in plain) plain else "$plain.0"
    }
    val ds = digits.unscaledValue().abs().toString()
    val mantissa = if (ds.length > 1) "${ds[0]}.${ds.substring(1)}" else ds
    val sign = if (x < 0) "-" else ""
    return "$sign${mantissa}e${if (exp < 0) "-" else "+"}${"%02d".format(Math.abs(exp))}"
}

/**
 * Python's `json.loads` on text, as gym-coach reads a session file: strict
 * (no control characters inside a string), `NaN` and `Infinity` accepted,
 * a later duplicate key winning, ints of any size up to Python's 4300-digit
 * limit, and a byte-order mark refused. Its failures are named as Python's
 * are, since the import report prints the name. Nesting past [MAX_JSON_DEPTH]
 * is refused as Python's RecursionError, which Python reaches at a depth its
 * own stack sets; the port refuses sooner, which only refuses a file no
 * client writes, and keeps a hostile one off the JVM's stack.
 */
internal fun pyJsonLoads(text: String): JsonElement = PyJsonParser(text).document()

internal const val MAX_JSON_DEPTH = 1000
private const val PY_INT_MAX_STR_DIGITS = 4300

private class PyJsonParser(private val s: String) {
    private var i = 0

    private fun fail(msg: String): Nothing = throw PyException("JSONDecodeError", true, "$msg: char $i")

    private fun ws() {
        while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
    }

    fun document(): JsonElement {
        if (s.startsWith('﻿')) fail("Unexpected UTF-8 BOM (decode using utf-8-sig)")
        ws()
        val v = value(0)
        ws()
        if (i != s.length) fail("Extra data")
        return v
    }

    private fun value(depth: Int): JsonElement {
        if (i >= s.length) fail("Expecting value")
        return when (s[i]) {
            '"' -> { i++; JsonPrimitive(string()) }
            '{' -> obj(depth + 1)
            '[' -> array(depth + 1)
            'n' -> word("null", JsonNull)
            't' -> word("true", JsonPrimitive(true))
            'f' -> word("false", JsonPrimitive(false))
            'N' -> word("NaN", JsonPrimitive(Double.NaN))
            'I' -> word("Infinity", JsonPrimitive(Double.POSITIVE_INFINITY))
            '-' -> if (s.startsWith("-Infinity", i)) word("-Infinity", JsonPrimitive(Double.NEGATIVE_INFINITY)) else number()
            else -> number()
        }
    }

    private fun word(w: String, v: JsonElement): JsonElement {
        if (!s.startsWith(w, i)) fail("Expecting value")
        i += w.length
        return v
    }

    private fun deeper(depth: Int) {
        if (depth > MAX_JSON_DEPTH) throw PyException("RecursionError", false, "nested deeper than $MAX_JSON_DEPTH")
    }

    private fun obj(depth: Int): JsonObject {
        deeper(depth)
        i++
        val out = LinkedHashMap<String, JsonElement>()
        ws()
        if (i < s.length && s[i] == '}') { i++; return JsonObject(out) }
        while (true) {
            if (i >= s.length || s[i] != '"') fail("Expecting property name enclosed in double quotes")
            i++
            val key = string()
            ws()
            if (i >= s.length || s[i] != ':') fail("Expecting ':' delimiter")
            i++
            ws()
            out[key] = value(depth)
            ws()
            if (i < s.length && s[i] == '}') { i++; return JsonObject(out) }
            if (i >= s.length || s[i] != ',') fail("Expecting ',' delimiter")
            i++
            ws()
        }
    }

    private fun array(depth: Int): JsonArray {
        deeper(depth)
        i++
        val out = mutableListOf<JsonElement>()
        ws()
        if (i < s.length && s[i] == ']') { i++; return JsonArray(out) }
        while (true) {
            out += value(depth)
            ws()
            if (i < s.length && s[i] == ']') { i++; return JsonArray(out) }
            if (i >= s.length || s[i] != ',') fail("Expecting ',' delimiter")
            i++
            ws()
        }
    }

    private fun string(): String {
        val out = StringBuilder()
        while (true) {
            if (i >= s.length) fail("Unterminated string starting at")
            val c = s[i++]
            when {
                c == '"' -> return out.toString()
                c < ' ' -> fail("Invalid control character at")
                c != '\\' -> out.append(c)
                i >= s.length -> fail("Unterminated string starting at")
                else -> when (val e = s[i++]) {
                    '"', '\\', '/' -> out.append(e)
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000c')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'u' -> {
                        val hex = if (i + 4 <= s.length) s.substring(i, i + 4) else ""
                        if (hex.length != 4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("Invalid \\uXXXX escape")
                        out.append(hex.toInt(16).toChar())
                        i += 4
                    }
                    else -> fail("Invalid \\escape")
                }
            }
        }
    }

    private fun digit() = i < s.length && s[i] in '0'..'9'

    private fun number(): JsonElement {
        val start = i
        if (s[i] == '-') i++
        when {
            i < s.length && s[i] in '1'..'9' -> while (digit()) i++
            i < s.length && s[i] == '0' -> i++
            else -> { i = start; fail("Expecting value") }
        }
        var isFloat = false
        if (i + 1 < s.length && s[i] == '.' && s[i + 1] in '0'..'9') {
            i++
            while (digit()) i++
            isFloat = true
        }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            val e = i++
            if (i < s.length && (s[i] == '-' || s[i] == '+')) i++
            val from = i
            while (digit()) i++
            if (i > from) isFloat = true else i = e
        }
        val literal = s.substring(start, i)
        if (isFloat) return JsonPrimitive(literal.toDouble())
        if (literal.removePrefix("-").length > PY_INT_MAX_STR_DIGITS) {
            throw PyException("ValueError", true, "Exceeds the limit ($PY_INT_MAX_STR_DIGITS digits) for integer string conversion")
        }
        val n = BigInteger(literal)
        return if (n.bitLength() < 64) JsonPrimitive(n.toLong()) else JsonPrimitive(n)
    }
}
