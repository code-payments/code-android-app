package com.getcode.model

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.PublicKey

data class StreamMessage(val id: List<Byte>, val kind: Kind) {
    sealed class Kind {
        class PaymentRequestKind(val paymentRequest: PaymentRequest) : Kind()
        class AirdropKind(val airdrop: Airdrop) : Kind()
    }

    val paymentRequest: PaymentRequest? =
        if (kind is Kind.PaymentRequestKind) kind.paymentRequest else null
    val airdrop: Airdrop? =
        if (kind is Kind.AirdropKind) kind.airdrop else null

    companion object {
        fun getInstance(message: MessagingService.Message): StreamMessage? {
            val kind: Kind = when (message.kindCase) {
                MessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL -> {
                    val account =
                        PublicKey(
                            message.requestToGrabBill.requestorAccount.value.toByteArray().toList()
                        )
                    val signature =
                        Signature(message.sendMessageRequestSignature.value.toByteArray().toList())

                    Kind.PaymentRequestKind(
                        PaymentRequest(
                            account = account,
                            signature = signature
                        )
                    )
                }
                MessagingService.Message.KindCase.AIRDROP_RECEIVED -> {
                    val type = AirdropType.getInstance(message.airdropReceived.airdropType)
                        ?: return null
                    val currency = CurrencyCode.tryValueOf(message.airdropReceived.exchangeData.currency)
                        ?: return null

                    Kind.AirdropKind(
                        Airdrop(
                            type = type,
                            date = message.airdropReceived.timestamp.seconds,
                            kinAmount = KinAmount.newInstance(
                                kin = Kin(quarks = message.airdropReceived.exchangeData.quarks),
                                rate = Rate(
                                    fx = message.airdropReceived.exchangeData.exchangeRate,
                                    currency = currency
                                )
                            )
                        )
                    )
                    return null
                }

                else -> return null
            }
            return StreamMessage(
                id = message.id.value.toByteArray().toList(),
                kind = kind
            )
        }
    }
}

data class PaymentRequest(val account: PublicKey, val signature: Signature)

data class Airdrop(val type: AirdropType, val date: Long, val kinAmount: KinAmount)