package com.getcode.model

import com.codeinc.gen.messaging.v1.MessagingService
import com.getcode.solana.keys.Signature
import com.getcode.solana.keys.PublicKey

data class StreamMessage(val id: List<Byte>, val kind: Kind) {
    sealed interface Kind {
        class ReceiveRequestKind(val receiveRequest: ReceiveRequest): Kind
        class PaymentRequestKind(val paymentRequest: PaymentRequest) : Kind
        class AirdropKind(val airdrop: Airdrop) : Kind
    }

    val receiveRequest: ReceiveRequest? = (kind as? Kind.ReceiveRequestKind)?.receiveRequest
    val paymentRequest: PaymentRequest? = (kind as? Kind.PaymentRequestKind)?.paymentRequest
    val airdrop: Airdrop? = (kind as? Kind.AirdropKind)?.airdrop

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
                MessagingService.Message.KindCase.REQUEST_TO_RECEIVE_BILL -> {
                    val request = message.requestToReceiveBill
                    val exchangeData = request.exchangeDataCase
                    val account = PublicKey(request.requestorAccount.value.toByteArray().toList())
                    val signature = Signature(message.sendMessageRequestSignature.value.toByteArray().toList())

                    val domain: Domain?
                    val verifier: PublicKey?
                    if (request.hasDomain()) {
                        val validDomain = Domain.from(request.domain.value) ?: return null
                        val validVerifier = PublicKey(request.verifier.value.toByteArray().toList())

                        domain = validDomain
                        verifier = validVerifier
                    } else {
                        domain = null
                        verifier = null
                    }

                    val requestData = when (exchangeData) {
                        MessagingService.RequestToReceiveBill.ExchangeDataCase.EXACT -> {
                            val data = request.exact
                            val currency = CurrencyCode.tryValueOf(data.currency) ?: return null

                            ReceiveRequest(
                                account = account,
                                signature = signature,
                                amount = ReceiveRequest.Amount.Exact(
                                    value = KinAmount.newInstance(
                                        kin = Kin(quarks = data.quarks),
                                        rate = Rate(
                                            fx = data.exchangeRate,
                                            currency = currency
                                        )
                                    )
                                ),
                                domain = domain,
                                verifier = verifier
                            )
                        }
                        MessagingService.RequestToReceiveBill.ExchangeDataCase.PARTIAL -> {
                            val data = request.partial
                            val currency = CurrencyCode.tryValueOf(data.currency) ?: return null

                            ReceiveRequest(
                                account = account,
                                signature = signature,
                                amount = ReceiveRequest.Amount.Partial(
                                    value = Fiat(currency, data.nativeAmount)
                                ),
                                domain = domain,
                                verifier = verifier
                            )
                        }
                        else -> return null
                    }

                    Kind.ReceiveRequestKind(requestData)
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

data class ReceiveRequest(
    val account: PublicKey,
    val signature: Signature,
    val amount: Amount,
    val domain: Domain?,
    val verifier: PublicKey?
) {
    sealed interface Amount {
        data class Exact(val value: KinAmount): Amount
        data class Partial(val value: Fiat): Amount
    }
}

data class PaymentRequest(val account: PublicKey, val signature: Signature)

data class Airdrop(val type: AirdropType, val date: Long, val kinAmount: KinAmount)