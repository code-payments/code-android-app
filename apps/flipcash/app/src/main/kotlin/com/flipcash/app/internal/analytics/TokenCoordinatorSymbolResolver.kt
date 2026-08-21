package com.flipcash.app.internal.analytics

import com.flipcash.app.analytics.TokenSymbolResolver
import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.solana.keys.Mint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds analytics' mint→ticker lookup to the token cache.
 *
 * Lives in `:app` because it is the only module that depends on both
 * `:shared:analytics` and `:shared:tokens`.
 */
@Module
@InstallIn(SingletonComponent::class)
object TokenSymbolResolverModule {

    @Provides
    @Singleton
    fun providesTokenSymbolResolver(
        tokenCoordinator: TokenCoordinator,
    ): TokenSymbolResolver = TokenSymbolResolver { mintBase58 ->
        // Cache-only and synchronous: analytics must never block or fetch.
        // An uncached mint yields null, and the property is omitted.
        runCatching { Mint(mintBase58) }.getOrNull()
            ?.let { tokenCoordinator.cachedToken(it) }
            ?.symbol
            ?.takeIf { it.isNotBlank() }
    }
}
