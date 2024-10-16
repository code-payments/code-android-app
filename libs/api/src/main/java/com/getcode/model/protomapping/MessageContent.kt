package com.getcode.model.protomapping

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v1.ChatService as ChatServiceV1
import com.getcode.model.EncryptedData
import com.getcode.model.Fiat
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.Reference
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Verb
import com.getcode.model.toPublicKey

operator fun MessageContent.Companion.invoke(
    proto: com.getcode.model.chat.MessageContentV2,
    isFromSelf: Boolean = false,
): MessageContent? {
    return when (proto.typeCase) {
        ChatService.Content.TypeCase.LOCALIZED -> MessageContent.Localized(
            isFromSelf = isFromSelf,
            value = proto.localized.keyOrText
        )

        ChatService.Content.TypeCase.EXCHANGE_DATA -> {
            val verb = Verb(proto.exchangeData.verb)
            when (proto.exchangeData.exchangeDataCase) {
                ChatService.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                    val exact = proto.exchangeData.exact
                    val currency =
                        com.getcode.model.CurrencyCode.tryValueOf(exact.currency) ?: return null
                    val kinAmount = KinAmount.newInstance(
                        kin = Kin.fromQuarks(exact.quarks),
                        rate = Rate(
                            fx = exact.exchangeRate,
                            currency = currency
                        )
                    )


                    val reference = Reference(proto.exchangeData)
                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Exact(kinAmount),
                        verb = verb,
                        reference = reference,
                        hasInteracted = false,
                    )
                }

                ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                    val partial = proto.exchangeData.partial
                    val currency =
                        com.getcode.model.CurrencyCode.tryValueOf(partial.currency) ?: return null

                    val fiat = Fiat(
                        currency = currency,
                        amount = partial.nativeAmount
                    )

                    val reference = Reference(proto.exchangeData)

                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Partial(fiat),
                        verb = verb,
                        reference = reference,
                        hasInteracted = false
                    )
                }

                ChatService.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                else -> return null
            }
        }

        ChatService.Content.TypeCase.NACL_BOX -> {
            val encryptedContent = proto.naclBox
            val peerPublicKey =
                encryptedContent.peerPublicKey.value.toByteArray().toPublicKey()

            val data = EncryptedData(
                peerPublicKey = peerPublicKey,
                nonce = encryptedContent.nonce.toByteArray().toList(),
                encryptedData = encryptedContent.encryptedPayload.toByteArray().toList(),
            )
            MessageContent.SodiumBox(isFromSelf = isFromSelf, data = data)
        }

        ChatService.Content.TypeCase.TEXT -> MessageContent.RawText(
            isFromSelf = isFromSelf,
            value = proto.text.text
        )

        ChatService.Content.TypeCase.TYPE_NOT_SET -> return null
        else -> return null
    }
}

fun MessageContent.Companion.fromV1(
    messageId: ID,
    proto: com.getcode.model.chat.MessageContentV1,
): MessageContent? {
    return when (proto.typeCase) {
        ChatServiceV1.Content.TypeCase.SERVER_LOCALIZED -> MessageContent.Localized(
            isFromSelf = false,
            value = proto.serverLocalized.keyOrText
        )

        ChatServiceV1.Content.TypeCase.EXCHANGE_DATA -> {
            val verb = Verb.fromV1(proto.exchangeData.verb)
            val isFromSelf = !verb.increasesBalance
            when (proto.exchangeData.exchangeDataCase) {
                ChatServiceV1.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                    val exact = proto.exchangeData.exact
                    val currency =
                        com.getcode.model.CurrencyCode.tryValueOf(exact.currency) ?: return null
                    val kinAmount = KinAmount.newInstance(
                        kin = Kin.fromQuarks(exact.quarks),
                        rate = Rate(
                            fx = exact.exchangeRate,
                            currency = currency
                        )
                    )

                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Exact(kinAmount),
                        verb = verb,
                        reference = Reference.IntentId(messageId),
                        hasInteracted = false,
                    )
                }

                ChatServiceV1.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                    val partial = proto.exchangeData.partial
                    val currency =
                        com.getcode.model.CurrencyCode.tryValueOf(partial.currency) ?: return null

                    val fiat = Fiat(
                        currency = currency,
                        amount = partial.nativeAmount
                    )

                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Partial(fiat),
                        verb = verb,
                        reference = Reference.IntentId(messageId),
                        hasInteracted = false
                    )
                }

                ChatServiceV1.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                else -> return null
            }
        }

        ChatServiceV1.Content.TypeCase.NACL_BOX -> {
            val encryptedContent = proto.naclBox
            val peerPublicKey =
                encryptedContent.peerPublicKey.value.toByteArray().toPublicKey()

            val data = EncryptedData(
                peerPublicKey = peerPublicKey,
                nonce = encryptedContent.nonce.toByteArray().toList(),
                encryptedData = encryptedContent.encryptedPayload.toByteArray().toList(),
            )
            MessageContent.SodiumBox(isFromSelf = false, data = data)
        }

        ChatServiceV1.Content.TypeCase.TYPE_NOT_SET -> return null
        else -> return null
    }
}