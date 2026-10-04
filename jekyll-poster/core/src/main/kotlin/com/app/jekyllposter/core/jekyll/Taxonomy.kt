package com.app.jekyllposter.core.jekyll

/** A category or tag the blog already uses, with how many posts use it. */
data class Term(val name: String, val count: Int)

/**
 * The categories and tags across a blog's posts, most used first, for pickers. Jekyll treats
 * "Travel" and "travel" as different categories but both become `/travel/` in URLs, so they're
 * merged here under the spelling most posts use, rather than offering both.
 */
data class Taxonomy(val categories: List<Term>, val tags: List<Term>) {
    companion object {
        val EMPTY = Taxonomy(emptyList(), emptyList())

        fun of(posts: Collection<PostSummary>): Taxonomy = Taxonomy(
            categories = count(posts.filterNot { it.path.isDraft }.map { it.categories }),
            tags = count(posts.filterNot { it.path.isDraft }.map { it.tags }),
        )

        private fun count(perPost: List<List<String>>): List<Term> = perPost
            .flatMap { terms -> terms.distinctBy { it.lowercase() } }
            .groupBy { it.lowercase() }
            .map { (_, spellings) ->
                val name = spellings.groupingBy { it }.eachCount().maxWith(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key }).key
                Term(name, spellings.size)
            }
            .sortedWith(compareByDescending<Term> { it.count }.thenBy { it.name.lowercase() })
    }
}
