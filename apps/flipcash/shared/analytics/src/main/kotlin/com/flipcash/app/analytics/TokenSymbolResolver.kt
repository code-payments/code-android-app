package com.flipcash.app.analytics

/**
 * Resolves a mint address to its ticker symbol for analytics properties.
 *
 * Declared here rather than depending on `:apps:flipcash:shared:tokens` directly:
 * that module already depends on this one, so the reverse edge would be a Gradle
 * cycle. The real implementation is bound in `:apps:flipcash:app`.
 */
fun interface TokenSymbolResolver {
    /** @return the ticker, or null when the mint is not cached. */
    fun symbolFor(mintBase58: String): String?

    companion object {
        /** Resolves nothing. Used where analytics is stubbed. */
        val None = TokenSymbolResolver { null }
    }
}
