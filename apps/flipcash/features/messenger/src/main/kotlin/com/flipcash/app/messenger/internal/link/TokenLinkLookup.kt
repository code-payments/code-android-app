package com.flipcash.app.messenger.internal.link

import com.flipcash.app.tokens.TokenCoordinator
import com.getcode.opencode.model.financial.Token
import com.getcode.solana.keys.Mint
import javax.inject.Inject

/**
 * Reads a mint's metadata for a token link card. The coordinator answers from memory, then the
 * database, then the network, so a mint the wallet already knows costs nothing.
 *
 * `AppRouter.handleTokenLink` does not check that the path segment is a real mint — it has no
 * reason to, since a bad one fails on the screen it opens. Here it means an unknown mint is an
 * ordinary miss, and a miss leaves the card unresolved rather than inventing a token.
 */
internal class TokenLinkLookup @Inject constructor(
    private val tokenCoordinator: TokenCoordinator,
) {
    suspend operator fun invoke(mint: Mint): Result<Token> = runCatching {
        requireNotNull(tokenCoordinator.getTokenMetadata(mint).getOrNull()?.token) {
            "no metadata for mint $mint"
        }
    }
}
