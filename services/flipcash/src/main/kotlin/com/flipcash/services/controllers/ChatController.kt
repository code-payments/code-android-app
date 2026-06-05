package com.flipcash.services.controllers

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.repository.ChatRepository
import com.flipcash.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatController @Inject constructor(
    private val repository: ChatRepository,
    private val userManager: UserManager,
) {
    suspend fun getChat(chatId: ChatId): Result<ChatMetadata> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getChat(owner, chatId)
    }

    suspend fun getDmChatFeed(queryOptions: QueryOptions = QueryOptions()): Result<ChatFeedPage> {
        val owner = userManager.accountCluster?.authority?.keyPair
            ?: return Result.failure(Throwable("No account cluster in UserManager"))

        return repository.getDmChatFeed(owner, queryOptions)
    }
}
