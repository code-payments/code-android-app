package com.getcode.model

import com.getcode.solana.keys.PublicKey
import com.getcode.utils.toByteString
import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService

sealed class IntentMetadata {
    data object OpenAccounts : IntentMetadata()
    data class SendPrivatePayment(val metadata: PaymentMetadata) : IntentMetadata()
    data class SendPublicPayment(val metadata: PaymentMetadata) : IntentMetadata()
    data object ReceivePaymentsPrivately : IntentMetadata()
    data class ReceivePaymentsPublicly(val metadata: PaymentMetadata) : IntentMetadata()
    data object UpgradePrivacy : IntentMetadata()

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
                        false,
                        null
                    )?.let { ReceivePaymentsPublicly(it) }
                }
                TransactionService.Metadata.TypeCase.UPGRADE_PRIVACY -> UpgradePrivacy
                TransactionService.Metadata.TypeCase.SEND_PRIVATE_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPrivatePayment.exchangeData.currency,
                        metadata.sendPrivatePayment.exchangeData.quarks,
                        metadata.sendPrivatePayment.exchangeData.exchangeRate,
                        metadata.sendPrivatePayment.isTip,
                        metadata.sendPrivatePayment.destination.toByteArray()
                    )?.let { SendPrivatePayment(it) }
                }
                TransactionService.Metadata.TypeCase.SEND_PUBLIC_PAYMENT -> {
                    getPaymentMetadata(
                        metadata.sendPublicPayment.exchangeData.currency,
                        metadata.sendPublicPayment.exchangeData.quarks,
                        metadata.sendPublicPayment.exchangeData.exchangeRate,
                        false,
                        metadata.sendPrivatePayment.destination.toByteArray()
                    )?.let { SendPublicPayment(it) }
                }
                else -> null
            }
        }

        private fun getPaymentMetadata(
            currencyString: String,
            quarks: Long,
            exchangeRate: Double,
            isTip: Boolean,
            destination: ByteArray?,
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
                isTip = isTip,
                destination = destination?.let { PublicKey.fromByteString(it.toByteString()) },
            )
        }
    }
}

data class PaymentMetadata(
    val amount: KinAmount,
    val isTip: Boolean,
    val destination: PublicKey?
)