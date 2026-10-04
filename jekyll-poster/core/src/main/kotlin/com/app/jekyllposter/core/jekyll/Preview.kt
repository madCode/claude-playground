package com.app.jekyllposter.core.jekyll

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.HtmlRenderer

/**
 * A post as a page, close enough to judge how it reads: Markdown rendered as kramdown would for
 * the common cases (GitHub-style tables and strikethrough too), and the Liquid that posts use for
 * links (`relative_url`, `absolute_url`, `site.baseurl`, `site.url`) resolved. A `post_url` link
 * goes nowhere: only Jekyll knows where it points. Other Liquid is left as written, since only
 * Jekyll can run it.
 *
 * @param resolveUrl turns a site path (`/assets/a.jpg`) into one the preview can load; images not
 *   on the site yet map to local files here.
 */
class Preview(private val config: SiteConfig, private val resolveUrl: (String) -> String) {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder()
        .extensions(extensions)
        // Raw HTML in a post is the writer's own, and Jekyll passes it through; the preview shows
        // it rather than escaping it. It's sandboxed in a WebView without JavaScript.
        .escapeHtml(false)
        .attributeProviderFactory { AttributeProvider { node: Node, _: String, attributes: MutableMap<String, String> ->
            when (node) {
                is Image -> attributes["src"]?.let { attributes["src"] = url(it) }
                is Link -> attributes["href"]?.let { attributes["href"] = url(it) }
            }
        } }
        .build()

    /** The post's body as an HTML fragment. */
    fun body(markdown: String): String = renderer.render(parser.parse(liquid(markdown)))

    /** A whole page: title, body and a stylesheet that follows the phone's light or dark theme. */
    fun page(title: String, markdown: String, dark: Boolean): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<style>").append(css(dark)).append("</style></head><body><article>")
        if (title.isNotBlank()) append("<h1>").append(escape(title)).append("</h1>")
        append(body(markdown))
        append("</article></body></html>")
    }

    /** Resolves the Liquid that makes links, so they point where the built site's would. */
    internal fun liquid(markdown: String): String = markdown
        // Left as written, its spaces would stop Markdown reading the link at all.
        .replace(postUrlTag, "#")
        .replace(filtered) { m ->
            val path = m.groupValues[2]
            when (m.groupValues[3]) {
                "absolute_url" -> config.url.orEmpty() + config.baseurl + path
                else -> config.baseurl + path
            }
        }
        .replace(baseurlTag, config.baseurl)
        .replace(urlTag, config.url.orEmpty())

    /** Site paths (they start with `/`) go through [resolveUrl], after taking the baseurl off. */
    private fun url(raw: String): String {
        if (!raw.startsWith("/") || raw.startsWith("//")) return raw
        val sitePath = if (config.baseurl.isNotEmpty() && raw.startsWith(config.baseurl + "/")) raw.removePrefix(config.baseurl) else raw
        return resolveUrl(sitePath)
    }

    private companion object {
        // Every brace escaped: Android's regex engine (ICU) refuses a bare `}`, where Java's takes it.
        val filtered = Regex("""\{\{\s*(['"])(.*?)\1\s*\|\s*(relative_url|absolute_url)\s*\}\}""")
        val baseurlTag = Regex("""\{\{\s*site\.baseurl\s*\}\}""")
        val urlTag = Regex("""\{\{\s*site\.url\s*\}\}""")
        val postUrlTag = Regex("""(\{\{\s*site\.baseurl\s*\}\})?\{%-?\s*post_url\s+\S+\s*-?%\}""")

        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        fun css(dark: Boolean): String {
            val (bg, fg, muted, rule) = if (dark) listOf("#1a1110", "#f1dfdb", "#c8b5b0", "#4a3a37") else listOf("#fffbf7", "#231918", "#6f5a53", "#e6d6d1")
            return """
                body{margin:0;background:$bg;color:$fg;font:18px/1.6 Georgia,'Noto Serif',serif}
                article{padding:20px 20px 64px;max-width:40em;margin:auto}
                h1{font-size:1.6em;line-height:1.25}
                img{max-width:100%;height:auto}
                a{color:${if (dark) "#ffb4a5" else "#b5341c"}}
                pre,code{font-family:monospace;font-size:.85em;background:$rule;border-radius:4px}
                pre{padding:12px;overflow-x:auto} pre code{background:none}
                blockquote{margin:0;padding-left:16px;border-left:3px solid $rule;color:$muted}
                table{border-collapse:collapse} td,th{border:1px solid $rule;padding:4px 8px}
                hr{border:0;border-top:1px solid $rule}
            """.trimIndent()
        }
    }
}
