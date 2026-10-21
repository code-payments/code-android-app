package com.flipcash.app.search.internal

import java.text.Normalizer

/**
 * How every Search chip matches: a case- and diacritic-insensitive substring of the trimmed query.
 * iOS matches the same way, so "jose" finds "José" on both.
 */
internal object SearchMatching {

    private val CombiningMarks = Regex("\\p{Mn}+")

    /** [raw] folded for comparison: decomposed, accents dropped, lowercased. */
    fun fold(raw: String): String =
        CombiningMarks.replace(Normalizer.normalize(raw, Normalizer.Form.NFD), "").lowercase()

    /** The folded query, or null when [raw] is blank and Search shows Recent instead. */
    fun query(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }?.let(::fold)

    /**
     * The chip after the query changes to [raw]: All once the query is blank, so the next search
     * starts there, and [current] while it still has text. iOS applies the same rule.
     */
    fun chipAfterQueryChange(current: SearchChip, raw: String): SearchChip =
        if (query(raw) == null) SearchChip.All else current

    /** Whether any of [fields] contains [query], which must already be folded by [query]. */
    fun matches(query: String, vararg fields: String?): Boolean =
        fields.any { field -> field != null && fold(field).contains(query) }
}
