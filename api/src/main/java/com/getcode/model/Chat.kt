package com.getcode.model

import com.codeinc.gen.chat.v1.ChatService
import com.codeinc.gen.chat.v1.ChatService.Content
import com.codeinc.gen.chat.v2.ChatService as ChatServiceV2
import com.codeinc.gen.chat.v2.ChatService.Content as MessageContentV2
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.network.repository.toPublicKey

typealias Cursor = List<Byte>

/**
 * Chat domain model for On-Chain messaging. This serves as a reference to a collection of messages.
 *
 * @param id Unique chat identifier ([ID])
 * @param cursor [Cursor] value for this chat for reference in subsequent GetChatsRequest
 * @param title Recommended chat [Title] inferred by the type of chat
 * @param pointer [Pointer] in the chat indicating the most recently read message by the user
 * @param unreadCount Estimated number of unread messages in this chat
 * @param canMute Can the user mute this chat?
 * @param isMuted Has the user muted this chat?
 * @param canUnsubscribe Can the user unsubscribe from this chat?
 * @param isSubscribed Is the user subscribed to this chat?
 * @param isVerified Is this a verified chat?
 *    NOTE: It's possible to have two chats with the same title, but with
 *    different verification statuses. They should be treated separately.
 * @param messages List of messages within this chat
 */
data class Chat(
    val id: ID,
    val cursor: Cursor,
    val title: Title?,
    val pointer: Pointer?,
    val unreadCount: Int,
    val canMute: Boolean,
    val isMuted: Boolean,
    val canUnsubscribe: Boolean,
    val isSubscribed: Boolean,
    val isVerified: Boolean,
    val messages: List<ChatMessage>
) {
    fun resetUnreadCount() = copy(unreadCount = 0)
    fun toggleMute() = copy(isMuted = !isMuted)

    val newestMessage: ChatMessage?
        get() = messages.maxByOrNull { it.dateMillis }

    val lastMessageMillis: Long?
        get() = newestMessage?.dateMillis
}

sealed interface Pointer {
    data object Unknown : Pointer
    data class Read(val id: ID) : Pointer

    companion object {
        operator fun invoke(proto: ChatService.Pointer): Pointer {
            return when (proto.kind) {
                ChatService.Pointer.Kind.UNKNOWN -> Unknown
                ChatService.Pointer.Kind.READ -> Read(proto.value.value.toList())
                ChatService.Pointer.Kind.UNRECOGNIZED -> Unknown
                else -> Unknown
            }
        }
    }

}

sealed interface Title {
    val value: String

    data class Localized(override val value: String) : Title
    data class Domain(override val value: String) : Title

    companion object {
        operator fun invoke(proto: ChatService.ChatMetadata): Title? {
            return when (proto.titleCase) {
                ChatService.ChatMetadata.TitleCase.LOCALIZED -> Localized(proto.localized.keyOrText)
                ChatService.ChatMetadata.TitleCase.DOMAIN -> Domain(proto.domain.value)
                ChatService.ChatMetadata.TitleCase.TITLE_NOT_SET -> null
                else -> null
            }
        }
    }
}

sealed interface Verb {
    val increasesBalance: Boolean

    data object Unknown : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Gave : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Received : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Withdrew : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Deposited : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Sent : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Returned : Verb {
        override val increasesBalance: Boolean = true
    }

    data object Spent : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Paid : Verb {
        override val increasesBalance: Boolean = false
    }

    data object Purchased : Verb {
        override val increasesBalance: Boolean = true
    }

    data object ReceivedTip : Verb {
        override val increasesBalance: Boolean = true
    }

    data object SentTip : Verb {
        override val increasesBalance: Boolean = false
    }

    companion object {
        @Deprecated("Replaced with v2")
        operator fun invoke(proto: ChatService.ExchangeDataContent.Verb): Verb {
            return when (proto) {
                ChatService.ExchangeDataContent.Verb.UNKNOWN -> Unknown
                ChatService.ExchangeDataContent.Verb.GAVE -> Gave
                ChatService.ExchangeDataContent.Verb.RECEIVED -> Received
                ChatService.ExchangeDataContent.Verb.WITHDREW -> Withdrew
                ChatService.ExchangeDataContent.Verb.DEPOSITED -> Deposited
                ChatService.ExchangeDataContent.Verb.SENT -> Sent
                ChatService.ExchangeDataContent.Verb.RETURNED -> Returned
                ChatService.ExchangeDataContent.Verb.SPENT -> Spent
                ChatService.ExchangeDataContent.Verb.PAID -> Paid
                ChatService.ExchangeDataContent.Verb.PURCHASED -> Purchased
                ChatService.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
                ChatService.ExchangeDataContent.Verb.RECEIVED_TIP -> ReceivedTip
                ChatService.ExchangeDataContent.Verb.SENT_TIP -> SentTip
            }
        }

        fun fromV2(proto: ChatServiceV2.ExchangeDataContent.Verb): Verb {
            return when (proto) {
                ChatServiceV2.ExchangeDataContent.Verb.UNKNOWN -> Unknown
                ChatServiceV2.ExchangeDataContent.Verb.GAVE -> Gave
                ChatServiceV2.ExchangeDataContent.Verb.RECEIVED -> Received
                ChatServiceV2.ExchangeDataContent.Verb.WITHDREW -> Withdrew
                ChatServiceV2.ExchangeDataContent.Verb.DEPOSITED -> Deposited
                ChatServiceV2.ExchangeDataContent.Verb.SENT -> Sent
                ChatServiceV2.ExchangeDataContent.Verb.RETURNED -> Returned
                ChatServiceV2.ExchangeDataContent.Verb.SPENT -> Spent
                ChatServiceV2.ExchangeDataContent.Verb.PAID -> Paid
                ChatServiceV2.ExchangeDataContent.Verb.PURCHASED -> Purchased
                ChatServiceV2.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
                ChatServiceV2.ExchangeDataContent.Verb.RECEIVED_TIP -> ReceivedTip
                ChatServiceV2.ExchangeDataContent.Verb.SENT_TIP -> SentTip
            }
        }
    }
}

data class ChatMessage(
    val id: ID,
    val cursor: Cursor,
    val dateMillis: Long,
    val contents: List<MessageContent>,
) {
    val hasEncryptedContent: Boolean
        get() {
            return contents.firstOrNull { it is MessageContent.SodiumBox } != null
        }

    fun decryptingUsing(keyPair: KeyPair): ChatMessage {
        return ChatMessage(
            id = id,
            dateMillis = dateMillis,
            cursor = cursor,
            contents = contents.map {
                when (it) {
                    is MessageContent.Exchange,
                    is MessageContent.Localized,
                    is MessageContent.Decrypted -> it // passthrough
                    is MessageContent.SodiumBox -> {
                        val decrypted = it.data.decryptMessageUsingNaClBox(keyPair = keyPair)
                        if (decrypted != null) {
                            MessageContent.Decrypted(decrypted)
                        } else {
                            it
                        }
                    }
                }
            }
        )
    }
}

sealed interface MessageContent {
    val isAnnouncement: Boolean
    val status: MessageStatus

    data class Localized(
        val value: String,
        override val isAnnouncement: Boolean = false,
        override val status: MessageStatus = MessageStatus.Unknown
    ) : MessageContent

    data class Exchange(
        val amount: GenericAmount,
        val verb: Verb,
        val thanked: Boolean = false,
        override val isAnnouncement: Boolean = false,
        override val status: MessageStatus = MessageStatus.Unknown
    ) : MessageContent

    data class SodiumBox(
        val data: EncryptedData,
        override val isAnnouncement: Boolean = false,
        override val status: MessageStatus = MessageStatus.Unknown
    ) : MessageContent

    data class Decrypted(
        val data: String,
        override val isAnnouncement: Boolean = false,
        override val status: MessageStatus = MessageStatus.Unknown
    ) : MessageContent

    companion object {
        @Deprecated("Replaced with v2")
        operator fun invoke(proto: Content): MessageContent? {
            return when (proto.typeCase) {
                Content.TypeCase.SERVER_LOCALIZED -> Localized(proto.serverLocalized.keyOrText)
                Content.TypeCase.EXCHANGE_DATA -> {
                    val verb = Verb(proto.exchangeData.verb)
                    val messageStatus = if (verb.increasesBalance) MessageStatus.Incoming else MessageStatus.Delivered
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

                            Exchange(GenericAmount.Exact(kinAmount), verb, status = messageStatus)
                        }

                        ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            Exchange(GenericAmount.Partial(fiat), verb, status = messageStatus)
                        }

                        ChatService.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                        else -> return null
                    }
                }

                Content.TypeCase.NACL_BOX -> {
                    val encryptedContent = proto.naclBox
                    val peerPublicKey =
                        encryptedContent.peerPublicKey.value.toByteArray().toPublicKey()

                    val data = EncryptedData(
                        peerPublicKey = peerPublicKey,
                        nonce = encryptedContent.nonce.toByteArray().toList(),
                        encryptedData = encryptedContent.encryptedPayload.toByteArray().toList(),
                    )
                    SodiumBox(data = data)
                }

                Content.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }

        fun fromV2(proto: MessageContentV2): MessageContent? {
            return when (proto.typeCase) {
                MessageContentV2.TypeCase.LOCALIZED -> Localized(proto.localized.keyOrText)
                MessageContentV2.TypeCase.EXCHANGE_DATA -> {
                    val verb = Verb.fromV2(proto.exchangeData.verb)
                    val messageStatus = if (verb.increasesBalance) MessageStatus.Incoming else MessageStatus.Delivered
                    when (proto.exchangeData.exchangeDataCase) {
                        ChatServiceV2.ExchangeDataContent.ExchangeDataCase.EXACT -> {
                            val exact = proto.exchangeData.exact
                            val currency = CurrencyCode.tryValueOf(exact.currency) ?: return null
                            val kinAmount = KinAmount.newInstance(
                                kin = Kin.fromQuarks(exact.quarks),
                                rate = Rate(
                                    fx = exact.exchangeRate,
                                    currency = currency
                                )
                            )

                            Exchange(GenericAmount.Exact(kinAmount), verb, status = messageStatus)
                        }

                        ChatServiceV2.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            Exchange(GenericAmount.Partial(fiat), verb, status = messageStatus)
                        }

                        ChatServiceV2.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                        else -> return null
                    }
                }

                MessageContentV2.TypeCase.NACL_BOX -> {
                    val encryptedContent = proto.naclBox
                    val peerPublicKey =
                        encryptedContent.peerPublicKey.value.toByteArray().toPublicKey()

                    val data = EncryptedData(
                        peerPublicKey = peerPublicKey,
                        nonce = encryptedContent.nonce.toByteArray().toList(),
                        encryptedData = encryptedContent.encryptedPayload.toByteArray().toList(),
                    )
                    SodiumBox(data = data)
                }

                MessageContentV2.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }
    }
}


