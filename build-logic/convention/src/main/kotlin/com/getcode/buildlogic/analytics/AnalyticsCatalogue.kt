package com.getcode.buildlogic.analytics

/** Reads `events.toml` and writes the builders and value enums it describes, and `EVENTS.md`. */
internal object AnalyticsCatalogue {
    fun parse(text: String): Catalogue = CatalogueParser.parse(text)

    /** The generated sources, keyed by path relative to the source root. */
    fun emit(catalogue: Catalogue): Map<String, String> = KotlinEmitter.emit(catalogue)

    /** `EVENTS.md`: the catalogue as a readable page. */
    fun document(catalogue: Catalogue): String = MarkdownEmitter.emit(catalogue)
}
