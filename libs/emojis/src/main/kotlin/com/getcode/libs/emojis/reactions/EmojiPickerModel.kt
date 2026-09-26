package com.getcode.libs.emojis.reactions

/**
 * The picker's pure grouping logic — search, category sections, the Frequently Used row, and the
 * undrawable filter — kept apart from the Compose sheet so it is testable without loading the real
 * catalog or standing up a screen. Mirrors iOS's `EmojiPickerModel`.
 */
object EmojiPickerModel {

    /** One section the grid draws, in order. */
    data class Section(
        val id: String,
        val title: String,
        val entries: List<EmojiCatalogEntry>,
    )

    /** The id [sections] gives the Frequently Used row, so the category bar can skip past it. */
    const val FREQUENTLY_USED_ID = "frequently-used"
    const val FREQUENTLY_USED_TITLE = "Frequently Used"

    /** The id [sections] gives the single section a non-empty search produces. */
    const val SEARCH_RESULTS_ID = "search-results"
    const val SEARCH_RESULTS_TITLE = "Search Results"

    /**
     * Builds the sections the grid draws.
     *
     * With an empty (or blank) [query]: a Frequently Used row built from [recents] — deduped and
     * left in the caller's order (the caller is expected to have already capped it to the picker's
     * row limit) — followed by one section per [catalog] category, in catalog order.
     *
     * With a non-empty [query]: a single "search results" section, catalog order, matched
     * case-insensitively against an entry's name or keywords — no Frequently Used row, since a
     * search is the user looking for something specific rather than reaching for the recent set.
     *
     * [undrawable] is filtered out of every section, including the Frequently Used row — a recent
     * pick this OS build can no longer render is worth dropping silently rather than showing a tofu
     * box for.
     */
    fun sections(
        catalog: EmojiCatalog,
        undrawable: Set<String>,
        recents: List<String>,
        query: String,
    ): List<Section> {
        val drawable = catalog.entries.filter { it.emoji !in undrawable }
        val trimmedQuery = query.trim()

        if (trimmedQuery.isNotEmpty()) {
            val needle = trimmedQuery.lowercase()
            val matches = drawable.filter { entry ->
                entry.name.lowercase().contains(needle) ||
                    entry.keywords.any { it.lowercase().contains(needle) }
            }
            return listOf(Section(id = SEARCH_RESULTS_ID, title = SEARCH_RESULTS_TITLE, entries = matches))
        }

        val sections = mutableListOf<Section>()
        if (recents.isNotEmpty()) {
            val byEmoji = drawable.associateBy { it.emoji }
            val seen = mutableSetOf<String>()
            val frequent = recents.mapNotNull { emoji ->
                if (!seen.add(emoji)) return@mapNotNull null
                byEmoji[emoji]
            }
            if (frequent.isNotEmpty()) {
                sections += Section(id = FREQUENTLY_USED_ID, title = FREQUENTLY_USED_TITLE, entries = frequent)
            }
        }
        for (category in catalog.categories) {
            val entries = drawable.filter { it.category == category }
            if (entries.isEmpty()) continue
            sections += Section(id = category, title = category, entries = entries)
        }
        return sections
    }
}
