package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.user.UserManager
import com.flipcash.shared.appfunctions.models.BalanceResult
import com.flipcash.shared.appfunctions.models.TokenBalanceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceFunctions @Inject constructor(
    private val userManager: UserManager,
    private val tokenCoordinator: TokenCoordinator,
) : GetBalanceSchema {
    /**
     * Returns the user's current wallet balance across all tokens.
     *
     * Each token's balance is expressed in USD. The total is the sum of all token balances.
     * Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun getBalance(context: AppFunctionContext): BalanceResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val balances = tokenCoordinator.tokenBalances.firstOrNull().orEmpty()
        val tokens = balances.map { twb ->
            TokenBalanceItem(
                symbol = twb.token.symbol,
                name = twb.token.name,
                balanceUsd = twb.balance.decimalValue,
            )
        }
        BalanceResult(
            totalUsd = tokens.sumOf { it.balanceUsd },
            tokens = tokens,
        )
    }
}
