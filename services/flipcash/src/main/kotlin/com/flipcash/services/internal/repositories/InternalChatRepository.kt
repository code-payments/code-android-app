package com.flipcash.services.internal.repositories

import com.flipcash.services.internal.domain.ChatMetadataMapper
import com.flipcash.services.internal.network.extensions.toPagingToken
import com.flipcash.services.internal.network.extensions.toViewerState
import com.flipcash.services.internal.network.services.ChatService
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.chat.MuteState
import com.flipcash.services.models.chat.StartChatParameters
import com.flipcash.services.models.chat.ViewerState
import com.flipcash.services.models.chat.ViewMode
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
        viewMode: ViewMode,
    ): Result<ChatMetadata> = service.getChat(owner, chatId, viewMode)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<ChatFeedPage> = service.getDmChatFeed(owner, queryOptions, chatType)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            ChatFeedPage(
                chats = response.chatsList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage> = service.getGroupChatFeed(owner, queryOptions)
        .onFailure { ErrorUtils.handleError(it) }
        .map { response ->
            ChatFeedPage(
                chats = response.chatsList.map { mapper.map(it) },
                pagingToken = if (response.hasPagingToken()) response.pagingToken.toPagingToken() else null,
                hasMore = response.hasMore,
            )
        }

    override suspend fun startChat(
        owner: KeyPair,
        parameters: StartChatParameters,
        idempotencyKey: IdempotencyKey,
    ): Result<ChatMetadata> = service.startChat(owner, parameters, idempotencyKey)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata> = service.joinChat(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { mapper.map(it) }

    override suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit> = service.leaveChat(owner, chatId)
        // Recovered before reporting, so an already-left chat is not logged as a failure.
        .recoverCatching { cause -> if (cause is LeaveChatError.NotFound) Unit else throw cause }
        .onFailure { ErrorUtils.handleError(it) }

    override suspend fun muteChat(
        owner: KeyPair,
        chatId: ChatId,
        mute: MuteState,
    ): Result<ViewerState> = service.muteChat(owner, chatId, mute)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toViewerState() }

    override suspend fun unmuteChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ViewerState> = service.unmuteChat(owner, chatId)
        .onFailure { ErrorUtils.handleError(it) }
        .map { it.toViewerState() }
}
