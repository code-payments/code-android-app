package com.getcode.model

sealed interface Value

data class Fiat(
    val currency: CurrencyCode,
    val amount: Double,
): Value

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