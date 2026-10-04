package com.app.jekyllposter.core.jekyll

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Where a post's new image goes on the site, and how the post links to it. */
object Images {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * A new image's site path beside the blog's others, in a folder per year, named by when it was
     * added: `/assets/images/2026/20261004-221500.jpg`. Named before the post has a final title,
     * so renaming the post doesn't break its links. [taken] holds paths to avoid.
     */
    fun sitePath(imageFolder: String, at: LocalDateTime, extension: String, taken: Set<String>): String {
        val base = "/${imageFolder.trim('/')}/${at.year}/${at.format(stamp)}"
        var path = "$base.$extension"
        var n = 2
        while (path in taken || path.removePrefix("/") in taken) path = "$base-${n++}.$extension"
        return path
    }

    /**
     * The Markdown for an image. The link goes through `relative_url` so it works on a project
     * site, which lives under `/repo/` and breaks a bare `/assets/…` link.
     */
    fun markdown(sitePath: String, alt: String): String =
        "![${alt.replace("[", "\\[").replace("]", "\\]").replace("\n", " ")}]({{ '$sitePath' | relative_url }})"

    /** [body] with the alt text of the image at [sitePath] set to [alt]. */
    fun withAlt(body: String, sitePath: String, alt: String): String {
        val link = Regex("""!\[((?:\\.|[^\]])*)]\(\{\{ '${Regex.escape(sitePath)}' \| relative_url }}\)""")
        return link.replace(body) { markdown(sitePath, alt.trim()) }
    }

    /** Whether [body] still links to [sitePath]; an image the writer deleted from the text isn't uploaded. */
    fun isUsed(body: String, sitePath: String): Boolean = body.contains(sitePath)
}
