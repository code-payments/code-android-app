package com.getcode.mapper

import com.getcode.model.Conversation
import com.getcode.model.KinAmount
import com.getcode.model.Rate
import com.getcode.model.chat.Chat
import com.getcode.model.chat.ChatMessage
import com.getcode.model.chat.MessageContent
import com.getcode.model.orOneToOne
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import javax.inject.Inject

class ConversationMapper @Inject constructor(
    private val exchange: Exchange,
) : Mapper<Pair<Chat, ChatMessage>, Conversation> {
    override fun map(from: Pair<Chat, ChatMessage>): Conversation {
        val (chat, message) = from
        val exchangeMessage = message.contents.firstOrNull {
            it is MessageContent.Exchange
        } as? MessageContent.Exchange

        val tipAmount = if (exchangeMessage != null) {
            val rate = exchange.rateFor(exchangeMessage.amount.currencyCode).orOneToOne()
            exchangeMessage.amount.amountUsing(rate)
        } else {
            KinAmount.newInstance(0, Rate.oneToOne)
        }

        val identity = chat.members.filterNot { it.isSelf }.firstNotNullOfOrNull { it.identity }

        return Conversation(
            messageIdBase58 = chat.id.base58,
            cursorBase58 = chat.cursor.base58,
            tipAmount = tipAmount,
            createdByUser = true, // only tippee can create a conversation
            hasRevealedIdentity = identity != null,
            lastActivity = null, // TODO: ?
            user = identity?.username,
            userImage = null,
        )
    }
}