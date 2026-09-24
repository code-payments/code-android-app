package com.flipcash.app.messenger.internal

import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.providers.TokenMetadataProvider
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * The token a balance rule names, for the gate bar's "Buy More <name>".
 *
 * Fetched first rather than only observed: the cache holds only mints the account has an account
 * for, so a viewer who has never held the rule's token would wait on it forever and Buy More would
 * never enable. The cache is followed after the fetch so the copy hydrated by a buy replaces it.
 */
internal fun TokenMetadataProvider.observeRuleToken(mint: Mint): Flow<Token> = flow {
    getTokenMetadata(mint).getOrNull()?.token?.let { emit(it) }
    emitAll(observeTokenCache().mapNotNull { it[mint] })
}
