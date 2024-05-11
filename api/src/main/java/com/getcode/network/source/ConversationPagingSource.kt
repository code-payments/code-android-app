package com.getcode.network.source

import com.getcode.db.AppDatabase
import com.getcode.db.Database
import com.getcode.model.ChatMessage
import com.getcode.model.Conversation
import com.getcode.model.ConversationMessage
import com.getcode.model.ConversationMessageContent
import com.getcode.model.ID
import com.getcode.model.MessageContent
import com.getcode.model.orOneToOne
import com.getcode.network.exchange.Exchange
import com.getcode.network.repository.base58
import org.kin.sdk.base.tools.toByteArray
import timber.log.Timber
import java.util.UUID

object ConversationMockProvider {

    val db: AppDatabase
        get() = Database.requireInstance()

    suspend fun createConversation(exchange: Exchange, message: ChatMessage): Conversation? {
        val ret = db.conversationDao().findConversationForMessage(message.id)
        val hasTipMessage = ret?.let { db.conversationDao().hasTipMessage(it.messageId) } ?: false
        if (hasTipMessage) return null

        val tipAmountRaw = message.contents.filterIsInstance<MessageContent.Exchange>()
            .map { it.amount }
            .firstOrNull() ?: return null

        val rate = exchange.rateFor(tipAmountRaw.currencyCode).orOneToOne()
        val tipAmount = tipAmountRaw.amountUsing(rate)

        val conversation = Conversation(
            messageIdBase58 = message.id.base58,
            cursorBase58 = message.id.base58,
            tipAmount = tipAmount,
            createdByUser = true,
            hasRevealedIdentity = false,
            user = null,
            userImage = null,
            lastActivity = null,
        )

        Timber.d("Created conversation from ${tipAmount.fiat}")

        return conversation
    }

    fun createMessage(
        conversationId: ID,
        content: ConversationMessageContent
    ): ConversationMessage {
        val mId = generateId()

        return ConversationMessage(
            idBase58 = mId.base58,
            cursorBase58 = mId.base58,
            conversationIdBase58 = conversationId.base58,
            dateMillis = System.currentTimeMillis(),
            content = content,
        )
    }

    suspend fun thankTipper(messageId: ID): ConversationMessage? {
        if (db.conversationDao().hasThanked(messageId)) {
            return null
        }

        return createMessage(messageId, ConversationMessageContent.ThanksSent)
    }

    suspend fun revealIdentity(messageId: ID): ConversationMessage? {
        val conversation =
            db.conversationDao().findConversationForMessage(messageId) ?: return null

        if (db.conversationDao().hasRevealedIdentity(conversation.messageId)) {
            return null
        }

        db.conversationDao().upsertConversations(conversation.copy(hasRevealedIdentity = true))

        return createMessage(conversation.messageId, ConversationMessageContent.IdentityRevealed)
    }

    private fun generateId() = UUID.randomUUID().toByteArray().toList()
}