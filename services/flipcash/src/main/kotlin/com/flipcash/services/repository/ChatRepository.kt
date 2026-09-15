package com.flipcash.services.repository

import com.flipcash.services.models.QueryOptions
import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.ChatFeedPage
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatRules
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

    suspend fun getGroupChatFeed(
        owner: KeyPair,
        queryOptions: QueryOptions,
    ): Result<ChatFeedPage>

    suspend fun startChat(
        owner: KeyPair,
        title: String,
        picture: BlobId?,
        rules: ChatRules?,
    ): Result<ChatMetadata>

    suspend fun joinChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<ChatMetadata>

    suspend fun leaveChat(
        owner: KeyPair,
        chatId: ChatId,
    ): Result<Unit>
}
