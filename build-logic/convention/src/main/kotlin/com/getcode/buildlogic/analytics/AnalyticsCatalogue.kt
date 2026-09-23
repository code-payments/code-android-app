package com.getcode.buildlogic.analytics

/** Reads `events.toml` and writes the builders and value enums it describes. */
internal object AnalyticsCatalogue {
    fun parse(text: String): Catalogue = CatalogueParser.parse(text)

    /** The generated sources, keyed by path relative to the source root. */
    fun emit(catalogue: Catalogue): Map<String, String> = KotlinEmitter.emit(catalogue)
}
