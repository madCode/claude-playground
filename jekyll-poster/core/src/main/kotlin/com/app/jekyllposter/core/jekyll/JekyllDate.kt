package com.app.jekyllposter.core.jekyll

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val zoned = listOf(
    "yyyy-MM-dd HH:mm:ss Z", "yyyy-MM-dd HH:mm:ss XXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd HH:mm Z",
    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd HH:mm:ss.SSS Z",
).map(DateTimeFormatter::ofPattern)

private val local = listOf(
    "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss.SSS",
).map(DateTimeFormatter::ofPattern)

/**
 * A front matter date as Jekyll reads it: `2025-03-02 18:05:00 -0800`; one without an offset, or a
 * bare day, is in the site's time zone [zone].
 */
fun parseJekyllDate(value: String, zone: ZoneId): ZonedDateTime? {
    val v = value.trim()
    zoned.forEach { f -> runCatching { return ZonedDateTime.parse(v, f) } }
    local.forEach { f -> runCatching { return LocalDateTime.parse(v, f).atZone(zone) } }
    // A bare day only: "2021-06-24 whatever" isn't a date the app should guess at.
    return runCatching { LocalDate.parse(v).atStartOfDay(zone) }.getOrNull()
}
