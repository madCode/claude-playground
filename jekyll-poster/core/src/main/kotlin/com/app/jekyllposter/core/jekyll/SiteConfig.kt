package com.app.jekyllposter.core.jekyll

import com.app.jekyllposter.core.frontmatter.FrontMatterDocument
import java.time.ZoneId

/** What the app needs from a site's `_config.yml`. */
data class SiteConfig(
    val title: String? = null,
    val url: String? = null,
    val baseurl: String = "",
    val permalink: String = "date",
    /** The site's time zone, when set; Jekyll otherwise uses the build machine's (UTC on GitHub). */
    val timezone: ZoneId? = null,
    /** Jekyll skips posts dated after the build unless this is set. */
    val future: Boolean = false,
    /** The layout posts get from `defaults`, so the app knows whether to write `layout:` itself. */
    val defaultPostLayout: String? = null,
) {
    companion object {
        fun parse(yaml: String?): SiteConfig {
            if (yaml.isNullOrBlank()) return SiteConfig()
            val map = FrontMatterDocument.parseYaml(yaml)
            return SiteConfig(
                title = map["title"]?.toString(),
                url = map["url"]?.toString()?.trimEnd('/'),
                baseurl = map["baseurl"]?.toString()?.trimEnd('/').orEmpty(),
                permalink = map["permalink"]?.toString() ?: "date",
                timezone = (map["timezone"] as? String)?.let { runCatching { ZoneId.of(it) }.getOrNull() },
                future = map["future"] == true,
                defaultPostLayout = postLayout(map["defaults"]),
            )
        }

        /** The layout from a `defaults` entry whose scope covers posts (`type: posts` or `path: _posts`). */
        private fun postLayout(defaults: Any?): String? {
            val list = defaults as? List<*> ?: return null
            return list.filterIsInstance<Map<*, *>>().firstNotNullOfOrNull { entry ->
                val scope = entry["scope"] as? Map<*, *> ?: return@firstNotNullOfOrNull null
                val coversPosts = scope["type"] == "posts" || (scope["path"] as? String)?.trim('/') == "_posts" ||
                    (scope["path"] == "" && scope["type"] == null)
                if (coversPosts) (entry["values"] as? Map<*, *>)?.get("layout")?.toString() else null
            }
        }
    }
}
