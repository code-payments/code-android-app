package com.getcode.oct24.inject

import androidx.paging.PagingData
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent
import com.getcode.oct24.domain.model.chat.ConversationWithLastPointers
import com.getcode.model.ID
import com.getcode.model.SocialUser
import com.getcode.model.chat.MessageStatus
import com.getcode.oct24.network.controllers.ConversationController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun providesConversationController(
    ): ConversationController = object : ConversationController {
        override fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> = flowOf(null)

        override suspend fun createConversation(identifier: ID, with: SocialUser): Conversation {
           throw NotImplementedError()
        }

        override suspend fun getConversation(identifier: ID): ConversationWithLastPointers? {
            return null
        }

        override suspend fun getOrCreateConversation(
            identifier: ID,
            with: SocialUser
        ): ConversationWithLastPointers {
            throw NotImplementedError()
        }

        override fun openChatStream(scope: CoroutineScope, conversation: Conversation) = Unit

        override fun closeChatStream() = Unit

        override suspend fun hasInteracted(messageId: ID): Boolean = false

        override suspend fun resetUnreadCount(conversationId: ID) = Unit

        override suspend fun advanceReadPointer(
            conversationId: ID,
            messageId: ID,
            status: MessageStatus
        ) = Unit

        override suspend fun sendMessage(
            conversationId: ID,
            message: String
        ): Result<ID> = Result.failure(Throwable())

        override fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessageWithContent>> {
            return emptyFlow()
        }

        override fun observeTyping(conversationId: ID): Flow<Boolean> = flowOf(false)

        override suspend fun onUserStartedTypingIn(conversationId: ID) = Unit

        override suspend fun onUserStoppedTypingIn(conversationId: ID) = Unit
    }
}