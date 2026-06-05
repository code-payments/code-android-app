package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.repository.ChatRepository
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.utils.ErrorUtils

internal class InternalChatRepository(
    private val service: ChatService,
    private val mapper: ChatMetadataMapper,
) : ChatRepository {
    override suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata> = service.getChat(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage> = service.getDmChatFeed(owner, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            ChatFeedPage(
                chats = response.chatsList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }
}
