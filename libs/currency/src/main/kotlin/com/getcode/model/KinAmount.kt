package com.getcode.model

import android.os.Parcelable
import com.getcode.model.Kin.Companion.fromKin
import com.getcode.utils.serializer.KinQuarksSerializer
import com.getcode.utils.serializer.RateAsStringSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class KinAmount(
    @Serializable(with = KinQuarksSerializer::class)
    val kin: Kin,
    val fiat: Double,
    @Serializable(with = RateAsStringSerializer::class)
    val rate: Rate
): Parcelable {
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

        fun newInstance(kin: Long, rate: Rate): KinAmount {
            return newInstance(fromKin(kin), rate)
        }

        fun fromQuarks(quarks: Long): KinAmount = newInstance(quarks, Rate.oneToOne)

        fun newInstance(kin: Kin, rate: Rate): KinAmount {
            return KinAmount(
                kin = kin,
                fiat = kin.toFiat(fx = rate.fx),
                rate = rate
            )
        }
    }
}

fun List<KinAmount>.sum(): KinAmount {
    if (this.isEmpty()) return KinAmount.Zero

    val totalKin = this.fold(Kin(0)) { acc, kinAmount -> acc + kinAmount.kin }
    val totalFiat = this.sumOf { it.fiat }
    val rate = this.first().rate // Assuming the rate is consistent or using the first item's rate

    return KinAmount(
        kin = totalKin,
        fiat = totalFiat,
        rate = rate
    )
}