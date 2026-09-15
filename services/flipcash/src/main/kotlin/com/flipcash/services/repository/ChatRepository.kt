package com.flipcash.services.repository

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.getcode.ed25519.Ed25519.KeyPair

interface ChatRepository {
    suspend fun getChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata>

    suspend fun getDmChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
        chatType: ChatType,
    ): Result<ChatFeedPage>

    /**
     * One page of the group feed. Unlike the DM feed there is no type filter: the server
     * decides what a group is, and `GetGroupChatFeedRequest` carries only query options.
     */
    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage>

    /** Adds the caller to [chatId]'s roster, returning the chat as the caller now sees it. */
    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata>

    /**
     * Removes the caller from [chatId]'s roster. Idempotent: a server `NOT_FOUND` is
     * reported as success, because it describes the state the call was asked to produce.
     */
    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit>
}
