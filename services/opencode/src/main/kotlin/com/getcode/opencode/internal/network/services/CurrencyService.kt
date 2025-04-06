package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.internal.network.core.NetworkOracle
import com.getcode.opencode.internal.network.managedApiRequest
import com.getcode.utils.CodeServerError
import kotlinx.datetime.Instant
import javax.inject.Inject

internal class CurrencyService @Inject constructor(
    private val api: CurrencyApi,
    private val networkOracle: NetworkOracle,
) {
    suspend fun getRates(
        from: Instant?
    ): Result<Map<String, Double>> {
        return networkOracle.managedApiRequest(
            call = { api.getAllRates(from?.toEpochMilliseconds()) },
            handleResponse = { response ->
                when (response.result) {
                    CurrencyService.GetAllRatesResponse.Result.OK -> Result.success(response.ratesMap)
                    CurrencyService.GetAllRatesResponse.Result.MISSING_DATA -> Result.failure(GetRatesError.MissingData())
                    CurrencyService.GetAllRatesResponse.Result.UNRECOGNIZED -> Result.failure(GetRatesError.Unrecognized())
                    else -> Result.failure(GetRatesError.Other())
                }
            },
            onOtherError = { cause ->
                Result.failure(GetRatesError.Other(cause = cause))
            }
        )
    }
}

sealed class GetRatesError(
    override val message: String? = null,
    override val cause: Throwable? = null
): CodeServerError(message, cause) {
    /**
     * No currency data is available for the requested timestamp.
     */
    class MissingData: GetRatesError()
    class Unrecognized: GetRatesError()
    data class Other(override val cause: Throwable? = null) : GetRatesError()
}