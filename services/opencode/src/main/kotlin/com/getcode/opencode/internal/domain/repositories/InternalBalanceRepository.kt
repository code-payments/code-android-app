package com.getcode.opencode.internal.domain.repositories

import com.getcode.opencode.internal.network.services.BalanceService
import com.getcode.opencode.model.core.errors.GetBalancesError
import com.getcode.opencode.model.financial.OwnerBalance
import com.getcode.opencode.repositories.BalanceRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.ErrorUtils
import javax.inject.Inject

internal class InternalBalanceRepository @Inject constructor(
    private val service: BalanceService,
) : BalanceRepository {
    override suspend fun getBalances(
        owners: List<PublicKey>,
        mints: List<PublicKey>,
    ): Result<List<OwnerBalance>> =
        service.getBalances(owners, mints)
            .onFailure { error ->
                if (error !is GetBalancesError.Denied) {
                    ErrorUtils.handleError(error)
                }
            }
}
