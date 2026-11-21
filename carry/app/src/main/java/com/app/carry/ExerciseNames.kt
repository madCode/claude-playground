package com.app.carry

import android.content.Context
import com.app.carry.brain.Kb

/** A movement the coach knows, and the other names a board writes it as. */
data class KnownName(val name: String, val aliases: List<String> = emptyList())

/**
 * Names to offer while the owner types an added row's name (madCode,
 * 2026-11-19). Picking one gives the row the coach's own name, which the
 * laptop's import matches as it is; anything else typed still stands, as a
 * row the coach doesn't know yet. Carry offers, the owner chooses: it never
 * names a row itself.
 *
 * The list is the coach's knowledge base as the brain fixtures carry it,
 * bundled at build time, so it is as current as the last fixtures change.
 */
object ExerciseNames {
    const val ASSET = "exercises.json"
    private const val MIN_TYPED = 2

    fun parse(text: String): List<KnownName> =
        Kb.fromJson(text).exercises.values.map { KnownName(it.name, it.aliases) }

    /** The bundled list, or none: a missing or broken asset costs the suggestions, never the row. */
    fun load(context: Context): List<KnownName> = runCatching {
        context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
    }.getOrDefault(emptyList())

    /**
     * Up to [limit] of [known]'s names for what is [typed], best first: a
     * name or alias that starts with it, then one with a word starting with
     * each typed word, a name before an alias, then shorter. Nothing once the
     * typed text is already one of the names, or is too short to mean much.
     */
    fun suggest(typed: String, known: List<KnownName>, limit: Int = 4): List<String> {
        val q = words(typed)
        if (q.joinToString(" ").length < MIN_TYPED) return emptyList()
        val query = q.joinToString(" ")
        if (known.any { words(it.name).joinToString(" ") == query }) return emptyList()
        return known.mapNotNull { k ->
            val scores = (listOf(k.name to 0) + k.aliases.map { it to 1 }).mapNotNull { (text, alias) ->
                val w = words(text)
                val joined = w.joinToString(" ")
                when {
                    joined.startsWith(query) -> 0 + alias
                    q.all { part -> w.any { it.startsWith(part) } } -> 2 + alias
                    else -> null
                }
            }
            scores.minOrNull()?.let { k.name to it }
        }
            .sortedWith(compareBy({ it.second }, { it.first.length }, { it.first }))
            .map { it.first }
            .distinct()
            .take(limit)
    }

    /** Lower case, split on anything that isn't a letter or digit: "Push-ups" is "push", "ups". */
    private fun words(s: String): List<String> =
        s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
}
