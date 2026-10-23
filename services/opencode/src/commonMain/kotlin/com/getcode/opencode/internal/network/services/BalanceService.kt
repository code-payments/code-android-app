package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.balance.v1.OcpBalanceService
import com.getcode.opencode.internal.domain.mapping.OwnerBalanceMapper
import com.getcode.opencode.internal.network.api.BalanceApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.errors.GetBalancesError
import com.getcode.opencode.model.financial.OwnerBalance
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

internal class BalanceService @Inject constructor(
    private val api: BalanceApi,
    private val ownerBalanceMapper: OwnerBalanceMapper,
) {
    suspend fun getBalances(
        owners: List<PublicKey>,
        mints: List<PublicKey> = emptyList(),
    ): Result<List<OwnerBalance>> {
        return runCatching {
            api.getBalances(owners, mints)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    OcpBalanceService.GetBalancesResponse.Result.OK -> Result.success(
                        response.balancesByOwnerMap.values.map { ownerBalanceMapper.map(it) }
                    )
                    OcpBalanceService.GetBalancesResponse.Result.DENIED -> Result.failure(
                        GetBalancesError.Denied())
                    OcpBalanceService.GetBalancesResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetBalancesError.Unrecognized())
                    else -> Result.failure(GetBalancesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetBalancesError.Other(cause = it) })
            }
        )
    }
}
