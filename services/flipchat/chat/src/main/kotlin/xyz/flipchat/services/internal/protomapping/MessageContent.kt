package xyz.flipchat.services.internal.protomapping

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.EncryptedData
import com.getcode.model.chat.MessageContent
import com.getcode.model.toPublicKey

operator fun MessageContent.Companion.invoke(
    proto: Model.Content,
    isFromSelf: Boolean = false,
): MessageContent? {
    return when (proto.typeCase) {
        Model.Content.TypeCase.LOCALIZED_ANNOUNCEMENT -> MessageContent.Announcement(
            isFromSelf = isFromSelf,
            value = proto.localizedAnnouncement.keyOrText
        )

//        Model.Content.TypeCase.EXCHANGE_DATA -> {
//            val verb = com.getcode.model.chat.Verb(proto.exchangeData.verb)
//            when (proto.exchangeData.exchangeDataCase) {
//                ChatService.ExchangeDataContent.ExchangeDataCase.EXACT -> {
//                    val exact = proto.exchangeData.exact
//                    val currency =
//                        com.getcode.model.CurrencyCode.tryValueOf(exact.currency) ?: return null
//                    val kinAmount = KinAmount.newInstance(
//                        kin = Kin.fromQuarks(exact.quarks),
//                        rate = Rate(
//                            fx = exact.exchangeRate,
//                            currency = currency
//                        )
//                    )
//
//
//                    val reference = com.getcode.model.chat.Reference(proto.exchangeData)
//                    MessageContent.Exchange(
//                        isFromSelf = isFromSelf,
//                        amount = GenericAmount.Exact(kinAmount),
//                        verb = verb,
//                        reference = reference,
//                        hasInteracted = false,
//                    )
//                }
//
//                ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
//                    val partial = proto.exchangeData.partial
//                    val currency =
//                        com.getcode.model.CurrencyCode.tryValueOf(partial.currency) ?: return null
//
//                    val fiat = Fiat(
//                        currency = currency,
//                        amount = partial.nativeAmount
//                    )
//
//                    val reference = com.getcode.model.chat.Reference(proto.exchangeData)
//
//                    MessageContent.Exchange(
//                        isFromSelf = isFromSelf,
//                        amount = GenericAmount.Partial(fiat),
//                        verb = verb,
//                        reference = reference,
//                        hasInteracted = false
//                    )
//                }
//
//                ChatService.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
//                else -> return null
//            }
//        }

        Model.Content.TypeCase.NACL_BOX -> {
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

        Model.Content.TypeCase.TEXT -> MessageContent.RawText(
            isFromSelf = isFromSelf,
            value = proto.text.text
        )

        Model.Content.TypeCase.TYPE_NOT_SET -> return null
        else -> return null
    }
}