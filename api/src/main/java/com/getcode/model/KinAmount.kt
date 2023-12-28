package com.getcode.model

import com.codeinc.gen.transaction.v2.TransactionService.ExchangeData
import com.getcode.utils.FormatUtils

data class KinAmount(
    val kin: Kin,
    val fiat: Double,
    val rate: Rate
) {
    fun truncating() = KinAmount(
        kin = kin.toKinTruncating(),
        fiat = fiat,
        rate = rate
    )

    companion object {
        fun newInstance(kin: Kin, rate: Rate): KinAmount {
            return KinAmount(
                kin = kin,
                fiat = kin.toFiat(fx = rate.fx),
                rate = rate
            )
        }

        fun fromFiatAmount(kin: Kin, fiat: Double, fx: Double, currencyCode: CurrencyCode): KinAmount {
            return KinAmount(
                kin = kin.inflating(),
                fiat = fiat,
                rate = Rate(
                    fx = fx,
                    currency = currencyCode
                )
            )
        }

        fun fromFiatAmount(fiat: Double, fx: Double, currencyCode: CurrencyCode): KinAmount {
            return fromFiatAmount(
                kin = Kin.fromFiat(fiat = fiat, fx = fx),
                fiat = fiat,
                fx = fx,
                currencyCode = currencyCode
            )
        }

        fun fromFiatAmount(fiat: Double, rate: Rate): KinAmount {
            return fromFiatAmount(
                kin = Kin.fromFiat(fiat = fiat, fx = rate.fx),
                fiat = fiat,
                fx = rate.fx,
                currencyCode = rate.currency
            )
        }

        fun fromProtoExchangeData(exchangeData: ExchangeData): KinAmount {
            return fromFiatAmount(
                kin = Kin(exchangeData.quarks),
                fiat = exchangeData.nativeAmount,
                fx = exchangeData.exchangeRate,
                currencyCode = CurrencyCode.tryValueOf(exchangeData.currency)!!
            )
        }
    }
}