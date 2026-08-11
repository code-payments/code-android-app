package com.getcode.opencode.providers

import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.Flow

/**
 * Provides token metadata resolution with cache-through semantics.
 *
 * This interface decouples token metadata retrieval from the persistence
 * and coordination layer. Service-layer consumers (e.g., Transactors)
 * depend on this contract without knowledge of caching or storage strategy.
 */
interface TokenMetadataProvider {
    suspend fun getTokenMetadata(mint: Mint): Result<TokenResult>

    /**
     * Observes the in-memory token cache as a `mint -> `[Token] map, re-emitting as tokens are
     * hydrated. Unlike [getTokenMetadata] this NEVER hits the network — it is a pure read of what
     * is already cached, so it is safe to resolve tokens synchronously inside hot paths (e.g. a
     * paging transform) without triggering per-item fetches. Mints the user holds no account for
     * are simply absent (they are never cached) rather than fetched on demand.
     */
    fun observeTokenCache(): Flow<Map<Mint, Token>>
}