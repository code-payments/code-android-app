package com.getcode.model

import com.codeinc.gen.chat.v1.ChatService
import com.codeinc.gen.chat.v1.ChatService.Content

typealias ID = List<Byte>
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
                ChatService.ChatMetadata.TitleCase.LOCALIZED -> Localized(proto.localized.key)
                ChatService.ChatMetadata.TitleCase.DOMAIN -> Domain(proto.domain.value)
                ChatService.ChatMetadata.TitleCase.TITLE_NOT_SET -> null
                else -> null
            }
        }
    }
}

sealed interface Verb {
    data object Unknown : Verb
    data object Gave : Verb
    data object Received : Verb
    data object Withdrew : Verb
    data object Deposited: Verb
    data object Sent : Verb
    data object Returned : Verb
    data object Spent : Verb
    data object Paid : Verb
    data object Purchased : Verb

    companion object {
        operator fun invoke(proto: ChatService.ExchangeDataContent.Verb): Verb {
            return when (proto) {
                ChatService.ExchangeDataContent.Verb.UNKNOWN -> Unknown
                ChatService.ExchangeDataContent.Verb.GAVE -> Gave
                ChatService.ExchangeDataContent.Verb.RECEIVED ->Received
                ChatService.ExchangeDataContent.Verb.WITHDREW -> Withdrew
                ChatService.ExchangeDataContent.Verb.DEPOSITED -> Deposited
                ChatService.ExchangeDataContent.Verb.SENT -> Sent
                ChatService.ExchangeDataContent.Verb.RETURNED -> Returned
                ChatService.ExchangeDataContent.Verb.SPENT -> Spent
                ChatService.ExchangeDataContent.Verb.PAID -> Paid
                ChatService.ExchangeDataContent.Verb.PURCHASED -> Purchased
                ChatService.ExchangeDataContent.Verb.UNRECOGNIZED -> Unknown
            }
        }
    }
}

data class ChatMessage(
    val id: ID,
    val cursor: Cursor,
    val dateMillis: Long,
    val contents: List<MessageContent>
)

sealed interface MessageContent {
    data class Localized(val value: String) : MessageContent
    data class Exchange(val amount: GenericAmount, val verb: Verb) : MessageContent
    data object SodiumBox : MessageContent

    companion object {
        operator fun invoke(proto: Content): MessageContent? {
            return when (proto.typeCase) {
                Content.TypeCase.LOCALIZED -> Localized(proto.localized.key)
                Content.TypeCase.EXCHANGE_DATA -> {
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

                            Exchange(GenericAmount.Exact(kinAmount), verb)
                        }
                        ChatService.ExchangeDataContent.ExchangeDataCase.PARTIAL -> {
                            val partial = proto.exchangeData.partial
                            val currency = CurrencyCode.tryValueOf(partial.currency) ?: return null

                            val fiat = Fiat(
                                currency = currency,
                                amount = partial.nativeAmount
                            )

                            Exchange(GenericAmount.Partial(fiat), verb)
                        }
                        ChatService.ExchangeDataContent.ExchangeDataCase.EXCHANGEDATA_NOT_SET -> return null
                        else -> return null
                    }
                }
                Content.TypeCase.NACL_BOX -> SodiumBox
                Content.TypeCase.TYPE_NOT_SET -> return null
                else -> return null
            }
        }
    }

}
