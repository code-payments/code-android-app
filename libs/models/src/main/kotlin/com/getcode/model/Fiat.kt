package com.getcode.model

import kotlinx.serialization.Serializable



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

@Serializable
sealed interface GenericAmount {

    val currencyCode: CurrencyCode

    @Serializable
    data class Exact(val amount: KinAmount): GenericAmount {
        override val currencyCode: CurrencyCode = amount.rate.currency
    }

    @Serializable
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

fun KinAmount.Companion.fromFiatAmount(kin: Kin, fiat: Double, fx: Double, currencyCode: CurrencyCode): KinAmount {
    return KinAmount(
        kin = kin.inflating(),
        fiat = fiat,
        rate = Rate(
            fx = fx,
            currency = currencyCode
        )
    )
}

fun KinAmount.Companion.fromFiatAmount(fiat: Double, fx: Double, currencyCode: CurrencyCode): KinAmount {
    return fromFiatAmount(
        kin = Kin.fromFiat(fiat = fiat, fx = fx),
        fiat = fiat,
        fx = fx,
        currencyCode = currencyCode
    )
}

fun KinAmount.Companion.fromFiatAmount(fiat: Double, rate: Rate): KinAmount {
    return fromFiatAmount(
        kin = Kin.fromFiat(fiat = fiat, fx = rate.fx),
        fiat = fiat,
        fx = rate.fx,
        currencyCode = rate.currency
    )
}

fun KinAmount.Companion.fromFiatAmount(fiat: Fiat, rate: Rate): KinAmount {
    return KinAmount(
        kin = Kin.fromFiat(fiat = fiat.amount, fx = rate.fx),
        fiat = fiat.amount,
        rate = rate
    )
}