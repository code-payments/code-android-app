package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.internal.network.services.BalanceService
import com.getcode.opencode.model.balance.Balance
import com.getcode.opencode.model.core.errors.GetBalanceError
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

internal class InternalBalanceRepository @Inject constructor(
    private val service: BalanceService,
) : BalanceRepository {
    override suspend fun getBalance(owner: PublicKey): Result<Balance> =
        service.getBalance(owner)
            .onFailure { error ->
                if (error !is GetBalanceError.NotFound && error !is GetBalanceError.Denied) {
                    ErrorUtils.handleError(error)
                }
            }
}
