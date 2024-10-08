package com.getcode.model

import com.codeinc.gen.transaction.v2.TransactionService

sealed class IntentMetadata {
    data object OpenAccounts : IntentMetadata()
    data class SendPrivatePayment(val metadata: PaymentMetadata) : IntentMetadata()
    data class SendPublicPayment(val metadata: PaymentMetadata) : IntentMetadata()
    data object ReceivePaymentsPrivately : IntentMetadata()
    data class ReceivePaymentsPublicly(val metadata: PaymentMetadata) : IntentMetadata()
    data object UpgradePrivacy : IntentMetadata()
    data object MigrateToPrivacy2022 : IntentMetadata()

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
                        metadata.sendPrivatePayment.isChat,
                    )?.let { ReceivePaymentsPublicly(it) }
                }
                TransactionService.Metadata.TypeCase.UPGRADE_PRIVACY -> UpgradePrivacy
                TransactionService.Metadata.TypeCase.MIGRATE_TO_PRIVACY_2022 -> MigrateToPrivacy2022
                TransactionService.Metadata.TypeCase.SEND_PRIVATE_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPrivatePayment.exchangeData.currency,
                        metadata.sendPrivatePayment.exchangeData.quarks,
                        metadata.sendPrivatePayment.exchangeData.exchangeRate,
                        metadata.sendPrivatePayment.isChat,
                    )?.let { SendPrivatePayment(it) }
                }
                TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPublicPayment.exchangeData.currency,
                        metadata.sendPrivatePayment.exchangeData.quarks,
                        metadata.sendPublicPayment.exchangeData.exchangeRate,
                        metadata.sendPrivatePayment.isChat,
                    )?.let { SendPublicPayment(it) }
                }
                else -> null
            }
        }

        private fun getPaymentMetadata(
            currencyString: String,
            quarks: Long,
            exchangeRate: Double,
            isChat: Boolean,
        ): PaymentMetadata? {
            val currency = CurrencyCode.tryValueOf(currencyString.uppercase())
                ?: return null

            return PaymentMetadata(
                amount = KinAmount.newInstance(
                    kin = Kin(quarks),
                    rate = Rate(
                        fx = exchangeRate,
                        currency = currency
                    )
                ),
                isChat = isChat,
            )
        }
    }
}

data class PaymentMetadata(
    val amount: KinAmount,
    val isChat: Boolean,
)