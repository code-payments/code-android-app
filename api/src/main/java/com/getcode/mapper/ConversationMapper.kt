package com.getcode.mapper

import com.getcode.model.ChatMessage
import com.getcode.model.Conversation
import com.getcode.model.KinAmount
import com.getcode.model.MessageContent
import com.getcode.model.Rate
import com.getcode.model.orOneToOne
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import javax.inject.Inject

class ConversationMapper @Inject constructor(
    private val exchange: Exchange,
) : Mapper<ChatMessage, Conversation> {
    override fun map(from: ChatMessage): Conversation {
        val exchangeMessage = from.contents.firstOrNull {
            it is MessageContent.Exchange
        } as? MessageContent.Exchange

        val tipAmount = if (exchangeMessage != null) {
            val rate = exchange.rateFor(exchangeMessage.amount.currencyCode).orOneToOne()
            exchangeMessage.amount.amountUsing(rate)
        } else {
            KinAmount.newInstance(0, Rate.oneToOne)
        }

        return Conversation(
            messageIdBase58 = from.id.base58,
            cursorBase58 = from.cursor.base58,
            tipAmount = tipAmount,
            createdByUser = false, // TODO: ?
            hasRevealedIdentity = false, // TODO: ?
            lastActivity = null,
            user = null, // TODO: ?
            userImage = null,
        )
    }
}