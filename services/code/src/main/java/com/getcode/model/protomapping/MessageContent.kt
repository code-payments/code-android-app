package com.getcode.model.protomapping

import com.codeinc.gen.chat.v1.ChatService
import com.getcode.model.EncryptedData
import com.getcode.model.Fiat
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.MessageContent
import com.getcode.model.chat.Reference
import com.getcode.model.chat.Verb
import com.getcode.model.toPublicKey

operator fun MessageContent.Companion.invoke(
    proto: ChatService.Content,
    messageId: ID? = null,
): MessageContent? {
    return when (proto.typeCase) {
        ChatService.Content.TypeCase.SERVER_LOCALIZED -> MessageContent.Localized(
            isFromSelf = false,
            value = proto.serverLocalized.keyOrText
        )

        ChatService.Content.TypeCase.EXCHANGE_DATA -> {
            val verb = Verb.invoke(proto.exchangeData.verb)
            val isFromSelf = !verb.increasesBalance
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

                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Exact(kinAmount),
                        verb = verb,
                        reference = messageId?.let { Reference.IntentId(it) },
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

                    MessageContent.Exchange(
                        isFromSelf = isFromSelf,
                        amount = GenericAmount.Partial(fiat),
                        verb = verb,
                        reference = messageId?.let { Reference.IntentId(it) },
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
            MessageContent.SodiumBox(isFromSelf = false, data = data)
        }

        ChatService.Content.TypeCase.TYPE_NOT_SET -> return null
        else -> return null
    }
}