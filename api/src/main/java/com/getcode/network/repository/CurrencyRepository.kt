package com.getcode.network.repository

import com.getcode.model.CurrencyRate
import com.getcode.network.core.NetworkOracle
import com.getcode.network.api.CurrencyApi
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.fixedRateTimer

@Singleton
class CurrencyRepository @Inject constructor(
    private val currencyApi: CurrencyApi,
    private val networkOracle: NetworkOracle
) {

    val ratesFlow = MutableStateFlow<List<CurrencyRate>>(emptyList())

    fun getRates(): Flow<Map<String, Double>> {
        return ratesFlow.map {
            it.associate { x -> x.id to x.rate }
        }.distinctUntilChanged()
    }

    fun getRatesAsMap(): Map<String, Double> {
        return ratesFlow.value.associate { x -> x.id to x.rate }
    }

    fun fetchRates() {
        currencyApi.getRates()
            .map { it.ratesMap.map { x -> CurrencyRate(x.key.uppercase(Locale.ROOT), x.value) } }
            .let { networkOracle.managedRequest(it) }
            .subscribe({
                ratesFlow.value = (additionalHardcodedRates + it)
            }, ErrorUtils::handleError)
    }

    companion object {
        private val additionalHardcodedRates = listOf(CurrencyRate("KIN", 1.0))
    }
}

