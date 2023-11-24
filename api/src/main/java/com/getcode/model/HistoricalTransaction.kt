package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.codeinc.gen.common.v1.Model

@Entity
data class HistoricalTransaction(
    @PrimaryKey(autoGenerate = true) val uid: Int? = null,
    val id: List<Byte>,
    val paymentType: PaymentType,
    val date: Long,
    val transactionRateFx: Double?,
    val transactionRateCurrency: String?,
    val transactionAmountQuarks: Long,
    val nativeAmount: Double,
    val isDeposit: Boolean,
    val isWithdrawal: Boolean,
    val isRemoteSend: Boolean,
    val isReturned: Boolean,
    val airdropType: AirdropType?,
) {
    fun getKinAmount(): KinAmount? {
        transactionRateFx ?: return null
        transactionRateCurrency ?: return null
        val currency = CurrencyCode.tryValueOf(transactionRateCurrency) ?: return null

        return KinAmount.newInstance(
            kin = Kin.fromQuarks(quarks = transactionAmountQuarks),
            rate = Rate(
                fx = transactionRateFx,
                currency = currency
            )
        )
    }
}

enum class PaymentType {
    Unknown,
    Send,
    Receive;

    companion object {
        fun tryValueOf(value: String): PaymentType? {
            return try {
                valueOf(value.lowercase().replaceFirstChar { it.uppercase() })
            } catch (e: Exception) {
                null
            }
        }
    }
}