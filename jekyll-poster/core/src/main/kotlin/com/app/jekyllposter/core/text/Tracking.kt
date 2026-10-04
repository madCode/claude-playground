package com.app.jekyllposter.core.text

/**
 * Takes the tracking codes out of the links in shared text: campaign tags (`utm_…`) and the click
 * and share ids that tie a visit back to who shared it (`fbclid`, `gclid`, YouTube's `si`, …).
 * Everything else in a link stays, since a parameter the app doesn't know may be the page itself.
 */
object Tracking {
    private val url = Regex("""https?://[^\s<>"'\])]+""")

    private val anywhere = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "yclid", "twclid", "ttclid", "li_fat_id",
        "igshid", "igsh", "mc_cid", "mc_eid", "_hsenc", "_hsmi", "mkt_tok", "oly_anon_id", "oly_enc_id",
        "vero_id", "wickedid", "rb_clickid", "s_cid",
    )

    /** Hosts whose `si` parameter is a share id rather than part of the address. */
    private val shareIdHosts = listOf("youtube.com", "youtu.be", "spotify.com")

    fun strip(text: String): String = url.replace(text) { match ->
        // Punctuation ending a sentence isn't part of the link before it.
        val link = match.value.trimEnd('.', ',', ';', ':', '!', '?')
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
        val kept = beforeFragment.substring(queryAt + 1).split('&').filter { pair ->
            val name = pair.substringBefore('=').lowercase()
            name.isNotEmpty() && !name.startsWith("utm_") && name !in anywhere &&
                !(name == "si" && shareIdHosts.any { host == it || host.endsWith(".$it") })
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }
}
