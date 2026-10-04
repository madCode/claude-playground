package com.app.jekyllposter.core.jekyll


/** Where a post's new image goes on the site, and how the post links to it. */
object Images {
    /**
     * A new image's site path beside the blog's others, in a folder per year, named [base]:
     * `/assets/images/2026/a-walk-to-the-lighthouse.jpg`, then `-2`, `-3`. [taken] holds paths to
     * avoid. Never a time: a photo's name shouldn't say when the writer was at their phone.
     */
    fun sitePath(imageFolder: String, year: Int, base: String, extension: String, taken: Set<String>): String {
        val stem = "/${imageFolder.trim('/')}/$year/${base.ifEmpty { "photo" }}"
        var path = "$stem.$extension"
        var n = 2
        while (path in taken || path.removePrefix("/") in taken) path = "$stem-${n++}.$extension"
        return path
    }

    /** Whether [path] is already named for the post [base] by [sitePath], in any year's folder. */
    fun isNamedFor(path: String, imageFolder: String, base: String): Boolean =
        Regex("""/${Regex.escape(imageFolder.trim('/'))}/\d{4}/${Regex.escape(base)}(-\d+)?\.[a-z]+""").matches(path)

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
