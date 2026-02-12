package com.getcode.opencode.providers

import com.getcode.opencode.model.financial.TokenResult
import com.getcode.solana.keys.Mint

/**
 * Provides token metadata resolution with cache-through semantics.
 *
 * This interface decouples token metadata retrieval from the persistence
 * and coordination layer. Service-layer consumers (e.g., Transactors)
 * depend on this contract without knowledge of caching or storage strategy.
 */
interface TokenMetadataProvider {
    suspend fun getTokenMetadata(mint: Mint): Result<TokenResult>
}