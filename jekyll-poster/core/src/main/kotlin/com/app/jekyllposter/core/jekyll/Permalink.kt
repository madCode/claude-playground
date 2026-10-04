package com.app.jekyllposter.core.jekyll

import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Where a post ends up on the site, worked out the way Jekyll does. */
object Permalink {
    private val styles = mapOf(
        "date" to "/:categories/:year/:month/:day/:title:output_ext",
        "pretty" to "/:categories/:year/:month/:day/:title/",
        "ordinal" to "/:categories/:year/:y_day/:title:output_ext",
        "weekdate" to "/:categories/:year/W:week/:short_day/:title:output_ext",
        "none" to "/:categories/:title:output_ext",
    )

    /**
     * The post's path on the site, e.g. `/writing/2025/04/20/reading-list.html`. The date is read
     * in the site's time zone, which on GitHub is UTC unless `_config.yml` sets one, so a post
     * written late in the evening west of Greenwich can have tomorrow's date in its URL.
     */
    fun path(config: SiteConfig, date: ZonedDateTime, slug: String, categories: List<String>): String {
        val template = styles[config.permalink] ?: config.permalink
        val local = date.withZoneSameInstant(config.timezone ?: ZoneOffset.UTC)
        val values = mapOf(
            // Jekyll lowercases categories in URLs and escapes the rest: a space becomes %20.
            "categories" to categories.map { escape(it.lowercase()) }.distinct().joinToString("/"),
            "year" to "%04d".format(local.year),
            "short_year" to "%02d".format(local.year % 100),
            "month" to "%02d".format(local.monthValue),
            "i_month" to local.monthValue.toString(),
            "day" to "%02d".format(local.dayOfMonth),
            "i_day" to local.dayOfMonth.toString(),
            "y_day" to "%03d".format(local.dayOfYear),
            "hour" to "%02d".format(local.hour),
            "minute" to "%02d".format(local.minute),
            "second" to "%02d".format(local.second),
            "title" to slug,
            "slug" to slug,
            "output_ext" to ".html",
        )
        // Longest names first, so `:short_year` isn't read as `:short` + `_year`.
        var out = template
        values.keys.sortedByDescending { it.length }.forEach { out = out.replace(":$it", values.getValue(it)) }
        return "/" + out.split('/').filter { it.isNotEmpty() }.joinToString("/") + if (out.endsWith("/")) "/" else ""
    }

    /** As Jekyll's URL escaping: letters, digits and `-._~` stay, the rest is percent-encoded. */
    private fun escape(segment: String): String = buildString {
        segment.toByteArray(Charsets.UTF_8).forEach { b ->
            val c = b.toInt().toChar()
            if (b >= 0 && (c.isLetterOrDigit() || c in "-._~!$&'()*+,;=:@")) append(c) else append("%%%02X".format(b.toInt() and 0xff))
        }
    }

    /**
     * The site's address, with no trailing slash: a custom domain from the `CNAME` file, else
     * `url` from `_config.yml`, else GitHub's own (`owner.github.io`, plus `/repo` for a project
     * site). The baseurl is added except for GitHub's project-site default, which already has it.
     */
    fun siteUrl(config: SiteConfig, owner: String, repo: String, cname: String?): String {
        val domain = cname?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        val userSite = repo.equals("${owner}.github.io", ignoreCase = true)
        return when {
            domain != null -> "https://$domain${config.baseurl}"
            // A project site built by Actions often leaves baseurl empty in _config.yml and gets
            // `--baseurl /repo` from the workflow, so the github.io address still needs the repo.
            !config.url.isNullOrBlank() && config.baseurl.isEmpty() && !userSite &&
                Regex("""^https?://[^/]+\.github\.io/?$""", RegexOption.IGNORE_CASE).matches(config.url) -> config.url.trimEnd('/') + "/" + repo
            !config.url.isNullOrBlank() -> config.url + config.baseurl
            userSite -> "https://${owner.lowercase()}.github.io${config.baseurl}"
            else -> "https://${owner.lowercase()}.github.io${config.baseurl.ifEmpty { "/$repo" }}"
        }
    }
}
