package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.balance.v1.OcpBalanceService
import com.getcode.opencode.internal.network.api.BalanceApi
import com.getcode.opencode.internal.network.extensions.foldWithSuppression
import com.getcode.opencode.model.core.errors.GetBalanceError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.utils.toValidationOrElse
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

internal class BalanceService @Inject constructor(
    private val api: BalanceApi,
) {
    suspend fun getBalance(owner: PublicKey): Result<Fiat> {
        return runCatching {
            api.getBalance(owner)
        }.foldWithSuppression(
            onSuccess = { response ->
                when (response.result) {
                    OcpBalanceService.GetBalanceResponse.Result.OK -> Result.success(
                        Fiat(quarks = response.coreMintValue, currencyCode = CurrencyCode.USD)
                    )
                    OcpBalanceService.GetBalanceResponse.Result.DENIED -> Result.failure(
                        GetBalanceError.Denied())
                    OcpBalanceService.GetBalanceResponse.Result.NOT_FOUND -> Result.failure(
                        GetBalanceError.NotFound())
                    OcpBalanceService.GetBalanceResponse.Result.UNRECOGNIZED -> Result.failure(
                        GetBalanceError.Unrecognized())
                    else -> Result.failure(GetBalanceError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(cause.toValidationOrElse { GetBalanceError.Other(cause = it) })
            }
        )
    }
}
