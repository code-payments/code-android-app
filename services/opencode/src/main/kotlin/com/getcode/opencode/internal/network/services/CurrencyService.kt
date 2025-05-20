package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.model.core.errors.GetRatesError
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class CurrencyService @Inject constructor(
    private val api: CurrencyApi,
) {
    suspend fun getRates(
        from: Instant?
    ): Result<Map<CurrencyCode, Rate>> {
        return runCatching {
            api.getAllRates(from?.toEpochMilliseconds())
        }.fold(
            onSuccess = { response ->
                when (response.result) {
                    CurrencyService.GetAllRatesResponse.Result.OK -> {
                        val rates = response.ratesMap
                            .mapNotNull { (key, value) ->
                                val currencyCode = CurrencyCode.tryValueOf(key)

                                currencyCode?.let { it to Rate(fx = value, currency = currencyCode) }
                            }
                            .toMap()

                        Result.success(rates)
                    }
                    CurrencyService.GetAllRatesResponse.Result.MISSING_DATA -> Result.failure(GetRatesError.MissingData())
                    CurrencyService.GetAllRatesResponse.Result.UNRECOGNIZED -> Result.failure(GetRatesError.Unrecognized())
                    else -> Result.failure(GetRatesError.Other())
                }
            },
            onFailure = { cause ->
                Result.failure(GetRatesError.Other(cause = cause))
            }
        )
    }
}