package com.getcode.oct24.network.controllers

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.codeinc.flipchat.gen.messaging.v1.MessagingService
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.oct24.domain.mapper.ConversationMapper
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent
import com.getcode.oct24.domain.model.chat.ConversationWithLastPointers
import com.getcode.oct24.internal.network.repository.chat.ChatRepository
import com.getcode.oct24.internal.network.repository.messaging.MessagingRepository
import com.getcode.services.model.chat.OutgoingMessageContent
import com.getcode.services.observers.BidirectionalStreamReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomController @Inject constructor(
    private val conversationMapper: ConversationMapper,
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
) {
    private val db: FcAppDatabase by lazy { FcAppDatabase.requireInstance() }

    fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    suspend fun createConversation(request: StartChatRequestType): Conversation {
        return chatRepository.startChat(request)
            .map { conversationMapper.map(it) }
            .onSuccess { db.conversationDao().upsertConversations(it) }
            .getOrThrow()
    }

    suspend fun getConversation(identifier: ID): ConversationWithLastPointers? {
        return db.conversationDao().findConversation(identifier)
    }

    suspend fun getOrCreateConversation(
        identifier: ID,
        recipients: List<ID>,
        title: String?
    ): ConversationWithLastPointers {
        val conversationByChatId = getConversation(identifier)
        if (conversationByChatId != null) {
            return conversationByChatId
        }

        // create request
        val request = if (recipients.count() == 1) {
            StartChatRequestType.TwoWay(recipients.first())
        } else {
            StartChatRequestType.Group(title, recipients)
        }

        return ConversationWithLastPointers(createConversation(request), emptyList())
    }

    fun openMessageStream(scope: CoroutineScope, conversation: Conversation) {
        runCatching { messagingRepository.openMessageStream(scope, conversation) }
    }

    fun closeMessageStream() {
        runCatching { messagingRepository.closeMessageStream() }
    }

    suspend fun resetUnreadCount(conversationId: ID) {

    }

    suspend fun advanceReadPointer(
        conversationId: ID,
        messageId: ID,
        status: MessageStatus
    ) {
        messagingRepository.advancePointer(conversationId, messageId, status)
    }

    suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val output = OutgoingMessageContent.Text(message)
        return messagingRepository.sendMessage(conversationId, output).map { it.id }
    }

    private val pagingConfig = PagingConfig(pageSize = 20)
    private fun conversationPagingSource(conversationId: ID) =
        db.conversationMessageDao().observeConversationMessages(conversationId)

    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessageWithContent>> {
        return Pager(
            config = pagingConfig,
            initialKey = null,
        ) { conversationPagingSource(conversationId) }.flow
    }

    fun observeTyping(conversationId: ID): Flow<Boolean> {
       return chatRepository.observeTyping(conversationId)
    }

    suspend fun onUserStartedTypingIn(conversationId: ID) {
        messagingRepository.onStartedTyping(conversationId)
    }

    suspend fun onUserStoppedTypingIn(conversationId: ID) {
        messagingRepository.onStoppedTyping(conversationId)
    }
}