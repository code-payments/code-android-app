package com.getcode.model

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.solana.keys.PublicKey

sealed class IntentMetadata {
    object OpenAccounts : IntentMetadata()
    data class SendPrivatePayment(val metadata: PaymentMetadata) : IntentMetadata()
    data class SendPublicPayment(val metadata: PaymentMetadata) : IntentMetadata()
    object ReceivePaymentsPrivately : IntentMetadata()
    data class ReceivePaymentsPublicly(val metadata: PaymentMetadata) : IntentMetadata()
    object UpgradePrivacy : IntentMetadata()
    object MigrateToPrivacy2022 : IntentMetadata()

    companion object {
        fun newInstance(metadata: TransactionService.Metadata): IntentMetadata? {
            return when (metadata.typeCase) {
                TransactionService.Metadata.TypeCase.OPEN_ACCOUNTS -> OpenAccounts
                TransactionService.Metadata.TypeCase.RECEIVE_PAYMENTS_PRIVATELY -> ReceivePaymentsPrivately
                TransactionService.Metadata.TypeCase.RECEIVE_PAYMENTS_PUBLICLY -> {
                    getPaymentMetadata(
                        metadata.receivePaymentsPublicly.exchangeData.currency,
                        metadata.receivePaymentsPublicly.exchangeData.quarks,
                        metadata.receivePaymentsPublicly.exchangeData.exchangeRate,
                    )?.let { ReceivePaymentsPublicly(it) }
                }
                TransactionService.Metadata.TypeCase.UPGRADE_PRIVACY -> UpgradePrivacy
                TransactionService.Metadata.TypeCase.MIGRATE_TO_PRIVACY_2022 -> MigrateToPrivacy2022
                TransactionService.Metadata.TypeCase.SEND_PRIVATE_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPrivatePayment.exchangeData.currency,
                        metadata.sendPrivatePayment.exchangeData.quarks,
                        metadata.sendPrivatePayment.exchangeData.exchangeRate,
                    )?.let { SendPrivatePayment(it) }
                }
                TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPublicPayment.exchangeData.currency,
                        metadata.sendPrivatePayment.exchangeData.quarks,
                        metadata.sendPublicPayment.exchangeData.exchangeRate,
                    )?.let { SendPublicPayment(it) }
                }
                else -> null
            }
        }

        private fun getPaymentMetadata(
            currencyString: String,
            quarks: Long,
            exchangeRate: Double,
        ): PaymentMetadata? {
            val currency = CurrencyCode.tryValueOf(currencyString.uppercase())
                ?: return null

            return PaymentMetadata(
                amount = KinAmount.newInstance(
                    kin = Kin(quarks = quarks),
                    rate = Rate(
                        fx = exchangeRate,
                        currency = currency
                    )
                )
            )
        }
    }
}

data class PaymentMetadata(
    val amount: KinAmount
)