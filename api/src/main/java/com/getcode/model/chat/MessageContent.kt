package com.getcode.model.chat

import com.codeinc.gen.chat.v1.ChatService as ChatServiceV1
import com.codeinc.gen.chat.v2.ChatService
import com.getcode.model.CurrencyCode
import com.getcode.model.EncryptedData
import com.getcode.model.Fiat
import com.getcode.model.GenericAmount
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.network.repository.toPublicKey
import com.getcode.utils.serializer.MessageContentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface MessageContent {
    val kind: Int
    val isFromSelf: Boolean

    @Serializable
    data class Localized(
        val value: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 0
    }

    @Serializable
    data class RawText(
        val value: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 1
    }

    @Serializable
    data class Exchange(
        val amount: GenericAmount,
        val verb: Verb,
        val reference: Reference,
        val hasInteracted: Boolean,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 2
    }

    @Serializable
    data class SodiumBox(
        val data: EncryptedData,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 3
    }

    @Serializable
    data class ThankYou(
        val tipIntentId: ID,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 4
    }

    @Serializable
    data class IdentityRevealed(
        val memberId: ID,
        val identity: Identity,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 5
    }

    @Serializable
    data class Decrypted(
        val data: String,
        override val isFromSelf: Boolean,
    ) : MessageContent {
        override val kind: Int = 6
    }

    companion object {
        operator fun invoke(
            proto: MessageContentV2,
            isFromSelf: Boolean = false,
        ): MessageContent? {
            return when (proto.typeCase) {
                ChatService.Content.TypeCase.LOCALIZED -> Localized(
                    isFromSelf = isFromSelf,
                    value = proto.localized.keyOrText
                )

                ChatService.Content.TypeCase.EXCHANGE_DATA -> {
                    val verb = Verb(proto.exchangeData.verb)
                    when (proto.exchangeData.exchangeDataCase) {
                        ChatService.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                            val exact = proto.exchangeData.exact
                            val currency = CurrencyCode.tryValueOf(exact.currency) ?: return null
                            val kinAmount = KinAmount.newInstance(
                                kin = Kin.fromQuarks(exact.quarks),
                                rate = Rate(
                                    fx = exact.exchangeRate,
                                    currency = currency
                                )
                            )


                            val reference = Reference(proto.exchangeData)
                            Exchange(
                                isFromSelf = isFromSelf,
                                amount = GenericAmount.Exact(kinAmount),
                                verb = verb,
                                reference = reference,
                                hasInteracted = false,
                            )
                        }

                        ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            val reference = Reference(proto.exchangeData)

                            Exchange(
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
                    SodiumBox(isFromSelf = isFromSelf, data = data)
                }

                ChatService.Content.TypeCase.THANK_YOU -> {
                    ThankYou(
                        isFromSelf = isFromSelf,
                        tipIntentId = proto.thankYou.tipIntent.value.toByteArray().toList()
                    )
                }

                ChatService.Content.TypeCase.IDENTITY_REVEALED -> {
                    IdentityRevealed(
                        isFromSelf = isFromSelf,
                        memberId = proto.identityRevealed.memberId.value.toByteArray().toList(),
                        identity = Identity(proto.identityRevealed.identity)
                    )
                }

                ChatService.Content.TypeCase.TEXT -> RawText(
                    isFromSelf = isFromSelf,
                    value = proto.text.text
                )

                ChatService.Content.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }

        fun fromV1(
            messageId: ID,
            proto: MessageContentV1,
        ): MessageContent? {
            return when (proto.typeCase) {
                ChatServiceV1.Content.TypeCase.LOCALIZED -> Localized(
                    isFromSelf = false,
                    value = proto.localized.keyOrText
                )

                ChatServiceV1.Content.TypeCase.EXCHANGE_DATA -> {
                    val verb = Verb.fromV1(proto.exchangeData.verb)
                    val isFromSelf = !verb.increasesBalance
                    when (proto.exchangeData.exchangeDataCase) {
                        ChatServiceV1.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                            val exact = proto.exchangeData.exact
                            val currency = CurrencyCode.tryValueOf(exact.currency) ?: return null
                            val kinAmount = KinAmount.newInstance(
                                kin = Kin.fromQuarks(exact.quarks),
                                rate = Rate(
                                    fx = exact.exchangeRate,
                                    currency = currency
                                )
                            )

                            Exchange(
                                isFromSelf = isFromSelf,
                                amount = GenericAmount.Exact(kinAmount),
                                verb = verb,
                                reference = Reference.IntentId(messageId),
                                hasInteracted = false,
                            )
                        }

                        ChatServiceV1.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            Exchange(
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
                    SodiumBox(isFromSelf = false, data = data)
                }

                ChatServiceV1.Content.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }
    }
}