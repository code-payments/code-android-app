package com.flipcash.shared.appfunctions.functions

import androidx.appfunctions.service.AppFunction
import androidx.appfunctions.AppFunctionContext
import com.flipcash.app.activityfeed.ActivityFeedCoordinator
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.description
import com.flipcash.shared.appfunctions.models.TransactionItem
import com.flipcash.shared.appfunctions.models.TransactionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionFunctions @Inject constructor(
    private val userManager: UserManager,
    private val activityFeedCoordinator: ActivityFeedCoordinator,
) : GetTransactionHistorySchema {
    /**
     * Returns recent transaction history for the user's wallet.
     *
     * @param limit Maximum number of transactions to return (default 20, max 100).
     * Each transaction includes a description, USD amount, timestamp, state, and type.
     * Requires the user to be logged in.
     */
    @AppFunction(isEnabled = false, isDescribedByKDoc = true)
    override suspend fun getTransactionHistory(
        context: AppFunctionContext,
        limit: Int,
    ): TransactionResult = withContext(Dispatchers.IO) {
        requireLoggedIn(userManager)
        val clamped = limit.coerceIn(1, 100)
        val messages = activityFeedCoordinator.getRecent(clamped)
        TransactionResult(
            transactions = messages.map { msg ->
                TransactionItem(
                    id = msg.id.description,
                    description = msg.text,
                    amountUsd = msg.amount?.underlyingTokenAmount?.decimalValue ?: 0.0,
                    timestamp = msg.timestamp.toEpochMilliseconds(),
                    state = msg.state.name,
                    type = msg.metadata?.let { it::class.simpleName } ?: "Unknown",
                )
            }
        )
    }
}
