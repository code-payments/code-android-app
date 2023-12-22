package com.getcode.network.repository

import com.getcode.model.CodePayload
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import timber.log.Timber
import javax.inject.Inject

data class Request(
    val amount: KinAmount,
    val payload: CodePayload,
)

class PaymentRepository @Inject constructor(
    private val currencyRepository: CurrencyRepository,
) {
    fun attempt(payload: CodePayload):  Request? {
        val fiat = payload.fiat
        if (fiat == null) {
            Timber.d("payload does not contain Fiat value")
            return null
        }

        val rateValue = currencyRepository.getRatesAsMap()[fiat.currency.name]
        if (rateValue == null) {
            Timber.d("Unable to determine rate")
            return null
        }

        val rate = Rate(rateValue, fiat.currency)

        Timber.d("Rate for ${rate.currency.name}: ${rate.fx}")
        Timber.d("fiat value = ${fiat.amount}")

        val amount = KinAmount.fromFiatAmount(fiat.amount, rate.fx, fiat.currency)

        Timber.d("amount=${amount.fiat}, ${amount.kin}, ${amount.rate}")

        return Request(amount, payload)
    }
}