package com.app.jekyllposter.core.text

/**
 * Takes the tracking codes out of the links in shared text: campaign tags (`utm_…`) and the click
 * and share ids that tie a visit back to who shared it (`fbclid`, `gclid`, YouTube's `si`, …).
 * Everything else in a link stays, since a parameter the app doesn't know may be the page itself.
 */
object Tracking {
    // Not * ` | : Markdown around a link (**bold**, `code`, a table cell) isn't part of it.
    private val url = Regex("""https?://[^\s<>"'\])*`|]+""")

    private val anywhere = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
        "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "wickedid", "rb_clickid", "s_cid",
    )

    /**
     * Parameters that only some sites use to say who shared a link, and that elsewhere may be the
     * page itself (`t` is a video's start time on YouTube): taken out only on those sites.
     */
    private val bySite = mapOf(
        "youtube.com" to setOf("si"), "youtu.be" to setOf("si"), "spotify.com" to setOf("si"),
        "x.com" to setOf("s", "t"), "twitter.com" to setOf("s", "t"),
        "reddit.com" to setOf("share_id", "rdt"),
        "tiktok.com" to setOf("_t", "_r", "is_from_webapp", "sender_device"),
        "linkedin.com" to setOf("trk", "trackingid", "lipi"),
        "substack.com" to setOf("r", "triedredirect"),
    )

    /** Sites with a domain in each country (amazon.de, google.co.uk). */
    private val byBrand = mapOf(
        "amazon" to setOf("tag", "ref", "ref_", "linkcode", "linkid"),
        "google" to setOf("ved", "ei", "sca_esv", "sxsrf"),
    )

    /** `www.amazon.co.uk` and the like: the brand, then only a country's endings, not `amazon.example.org`. */
    private val brandHost = Regex("""^(?:www\.|smile\.)?([a-z]+)\.(?:com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    private fun siteParams(host: String): Set<String> {
        val site = bySite.entries.firstOrNull { (h, _) -> host == h || host.endsWith(".$h") }?.value.orEmpty()
        val brand = brandHost.find(host)?.groupValues?.get(1)
        return site + byBrand[brand].orEmpty()
    }

    fun strip(text: String): String = url.replace(text) { match ->
        // Punctuation ending a sentence isn't part of the link before it.
        val link = match.value.trimEnd('.', ',', ';', ':', '!', '?', '_')
        stripLink(link) + match.value.substring(link.length)
    }

    private fun stripLink(link: String): String {
        val hashAt = link.indexOf('#')
        val fragment = if (hashAt >= 0) link.substring(hashAt) else ""
        val beforeFragment = if (hashAt >= 0) link.substring(0, hashAt) else link
        val queryAt = beforeFragment.indexOf('?')
        if (queryAt < 0) return link
        val base = beforeFragment.substring(0, queryAt)
        val host = base.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
        val query = beforeFragment.substring(queryAt + 1)
        // Text copied from HTML can carry its links' & as &amp;.
        val separator = if ("&amp;" in query) "&amp;" else "&"
        val kept = query.split(separator).filter { pair ->
            val name = pair.substringBefore('=').lowercase()
            name.isNotEmpty() && !name.startsWith("utm_") && name !in anywhere && name !in siteParams(host)
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString(separator)) + fragment
    }
}
