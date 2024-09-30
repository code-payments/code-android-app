package com.getcode.network.service

import com.getcode.model.CurrencyCode
import com.getcode.model.Rate
import com.getcode.network.api.CurrencyApi
import com.getcode.network.core.NetworkOracle
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.convert
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

data class ApiRateResult(
    val rates: List<Rate>,
    val dateMillis: Long,
)

class CurrencyService @Inject constructor(
    private val api: CurrencyApi,
    private val networkOracle: NetworkOracle,
) {
    @OptIn(ExperimentalTime::class)
    suspend fun getRates(): Result<ApiRateResult> {
        Timber.d("fetching rates")
        return try {
            networkOracle.managedRequest(api.getRates())
                .map { response ->
                    val rates = response.ratesMap.mapNotNull { (key, value) ->
                        val currency = com.getcode.model.CurrencyCode.tryValueOf(key) ?: return@mapNotNull null
                        Rate(fx = value, currency = currency)
                    }.toMutableList()

                    if (rates.none { it.currency == com.getcode.model.CurrencyCode.KIN }) {
                        rates.add(Rate(fx = 1.0, currency = com.getcode.model.CurrencyCode.KIN))
                    }

                    Result.success(ApiRateResult(
                        rates = rates.toList(),
                        dateMillis = convert(
                            value = response.asOf.seconds.toDouble(),
                            sourceUnit = DurationUnit.SECONDS,
                            targetUnit = DurationUnit.MILLISECONDS
                        ).toLong()
                    ))
                }.first()
        } catch (e: Exception) {
            ErrorUtils.handleError(e)
            Result.failure(e)
        }
    }
}