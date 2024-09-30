package com.getcode.model

import com.getcode.model.Kin.Companion.fromKin
import com.getcode.utils.serializer.KinQuarksSerializer
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
        val Zero = newInstance(0, Rate.oneToOne)

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
    }
}