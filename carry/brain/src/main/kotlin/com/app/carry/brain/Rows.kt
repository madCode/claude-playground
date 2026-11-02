package com.app.carry.brain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.math.BigDecimal
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
