package com.getcode.oct24.network.controllers

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.getcode.model.ID
import com.getcode.model.chat.MessageStatus
import com.getcode.model.uuid
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent
import com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers
import com.getcode.oct24.internal.network.repository.chat.ChatRepository
import com.getcode.oct24.internal.network.repository.messaging.MessagingRepository
import com.getcode.services.model.chat.OutgoingMessageContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomController @Inject constructor(
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
) {
    private val db: FcAppDatabase by lazy { FcAppDatabase.requireInstance() }

    fun observeConversation(id: ID): Flow<ConversationWithMembersAndLastPointers?> {
        return db.conversationDao().observeConversation(id)
    }

    suspend fun getConversation(identifier: ID): ConversationWithMembersAndLastPointers? {
        return db.conversationDao().findConversation(identifier)
    }

    fun openMessageStream(scope: CoroutineScope, conversation: Conversation) {
        runCatching { messagingRepository.openMessageStream(scope, conversation) }
    }

    fun closeMessageStream() {
        runCatching { messagingRepository.closeMessageStream() }
    }

    suspend fun resetUnreadCount(conversationId: ID) {
        db.conversationDao().resetUnreadCount(conversationId)
    }

    suspend fun advancePointer(
        conversationId: ID,
        messageId: ID,
        status: MessageStatus
    ) {
        when (status) {
            MessageStatus.Sent -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        db.conversationPointersDao().insert(conversationId, messageId.uuid!!, status)
                    }
            }
            MessageStatus.Delivered -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        db.conversationPointersDao().insert(conversationId, messageId.uuid!!, status)
                    }
            }
            MessageStatus.Read -> {
                messagingRepository.advancePointer(conversationId, messageId, status)
                    .onSuccess {
                        db.conversationPointersDao().insert(conversationId, messageId.uuid!!, status)
                    }
            }
            MessageStatus.Unknown -> Unit
        }
    }

    suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
        val output = OutgoingMessageContent.Text(message)
        return messagingRepository.sendMessage(conversationId, output)
            .map { it.id }
    }

    suspend fun deleteMessage(conversationId: ID, messageId: ID): Result<Unit> {
        return messagingRepository.deleteMessage(conversationId, messageId)
            .onSuccess {
                db.conversationMessageDao().markDeletedAndRemoveContents(messageId)
            }
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

    suspend fun leaveRoom(conversationId: ID): Result<Unit> {
        return chatRepository.leaveChat(conversationId)
            .onSuccess {
                db.conversationDao().deleteConversationById(conversationId)
                db.conversationPointersDao().deletePointerForConversation(conversationId)
                db.conversationMessageDao().removeForConversation(conversationId)
            }
    }
}