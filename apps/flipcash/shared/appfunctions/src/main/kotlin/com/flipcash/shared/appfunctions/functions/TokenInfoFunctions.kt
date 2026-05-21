package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.appfunctions.models.TokenInfoResult
import com.getcode.solana.keys.base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenInfoFunctions @Inject constructor(
    private val userManager: UserManager,
    private val tokenCoordinator: TokenCoordinator,
) : GetTokenInfoSchema {
    /**
     * Returns detailed information about a specific token including its balance.
     *
     * @param tokenSymbol The symbol of the token to look up (e.g. "USDF", "MOONY").
     * Returns the token name, symbol, current balance in USD, mint address, and description.
     * Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun getTokenInfo(
        context: AppFunctionContext,
        tokenSymbol: String,
    ): TokenInfoResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val token = tokenCoordinator.getTokenBySymbol(tokenSymbol)
            ?: throw AppFunctionElementNotFoundException("Token '$tokenSymbol' not found")
        val balance = tokenCoordinator.balanceForToken(token)
        TokenInfoResult(
            symbol = token.symbol,
            name = token.name,
            balanceUsd = balance.decimalValue,
            marketCapUsd = token.marketCap()?.decimalValue ?: 0.0,
            mintAddress = token.address.base58(),
            description = token.description,
        )
    }
}
