package com.getcode.model

import kotlinx.serialization.Serializable

sealed interface Value

@Serializable
data class Fiat(
    val currency: CurrencyCode,
    val amount: Double,
): Value {

    companion object {
        fun fromString(currency: CurrencyCode, amountString: String): Fiat? {
            val amount = amountString.toDoubleOrNull() ?: return null
            return Fiat(
                currency = currency,
                amount = amount
            )
        }
    }
}

sealed interface GenericAmount {

    val currencyCode: CurrencyCode
    data class Exact(val amount: KinAmount): GenericAmount {
        override val currencyCode: CurrencyCode = amount.rate.currency
    }
    data class Partial(val fiat: Fiat): GenericAmount {
        override val currencyCode: CurrencyCode = fiat.currency
    }

    fun amountUsing(rate: Rate): KinAmount {
        return when (this) {
            is Exact -> amount
            is Partial -> KinAmount.fromFiatAmount(fiat.amount, rate)
        }
    }
}