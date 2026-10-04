package com.app.jekyllposter.core.jekyll

import java.text.Normalizer

/**
 * A post's file-name slug, close to Jekyll's own `slugify` (default mode): lower case, runs of
 * anything but letters and digits become one hyphen. Accents are dropped so the URL stays plain
 * ASCII where it can; letters with no ASCII form (Cyrillic, CJK) are kept, as Jekyll keeps them.
 */
object Slug {
    private val marks = Regex("""\p{M}+""")
    private val separators = Regex("""[^\p{L}\p{Nd}]+""")

    fun of(title: String, maxLength: Int = 60): String {
        val folded = Normalizer.normalize(title, Normalizer.Form.NFD).replace(marks, "")
        val slug = folded.lowercase().replace(separators, "-").trim('-')
        if (slug.length <= maxLength) return slug
        // Cut at a word boundary so a long title doesn't end mid-word.
        val cut = slug.take(maxLength)
        val lastHyphen = cut.lastIndexOf('-')
        return (if (lastHyphen > maxLength / 2) cut.take(lastHyphen) else cut).trim('-')
    }
}
