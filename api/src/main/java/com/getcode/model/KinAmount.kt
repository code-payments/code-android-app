package com.getcode.model

import com.codeinc.gen.transaction.v2.TransactionService.ExchangeData
import com.getcode.model.Kin.Companion.fromKin
import com.getcode.utils.FormatUtils
import com.getcode.utils.serializer.KinQuarksSerializer
import com.getcode.utils.serializer.PublicKeyAsStringSerializer
import com.getcode.utils.serializer.RateAsStringSerializer
import kotlinx.serialization.Serializable

@Serializable
data class KinAmount(
    @Serializable(with = KinQuarksSerializer::class)
    val kin: Kin,
    val fiat: Double,
    @Serializable(with = RateAsStringSerializer::class)
    val rate: Rate
) {
    fun truncating() = KinAmount(
        kin = kin.toKinTruncating(),
        fiat = fiat,
        rate = rate
    )

    fun replacing(rate: Rate): KinAmount {
        return newInstance(this.kin, rate)
    }

    companion object {
        fun newInstance(kin: Int, rate: Rate): KinAmount {
            return newInstance(fromKin(kin), rate)
        }

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

        fun fromFiatAmount(fiat: Fiat, rate: Rate): KinAmount {
            return KinAmount(
                kin = Kin.fromFiat(fiat = fiat.amount, fx = rate.fx),
                fiat = fiat.amount,
                rate = rate
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