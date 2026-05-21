package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionElementNotFoundException
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.appfunctions.models.DepositAddressResult
import com.getcode.solana.keys.base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepositFunctions @Inject constructor(
    private val userManager: UserManager,
    private val tokenCoordinator: TokenCoordinator,
) : GetDepositAddressSchema {
    /**
     * Returns the Solana deposit address for a given token.
     *
     * @param tokenSymbol The token symbol to get the deposit address for (default "USDF").
     * Use this address to receive tokens from external wallets. Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun getDepositAddress(
        context: AppFunctionContext,
        tokenSymbol: String,
    ): DepositAddressResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val cluster = userManager.accountCluster
            ?: throw AppFunctionElementNotFoundException("Account not available")
        val token = tokenCoordinator.getTokenBySymbol(tokenSymbol)
            ?: throw AppFunctionElementNotFoundException("Token '$tokenSymbol' not found")
        val address = cluster.depositAddressFor(token)
        DepositAddressResult(
            address = address.base58(),
            tokenSymbol = token.symbol,
        )
    }
}
