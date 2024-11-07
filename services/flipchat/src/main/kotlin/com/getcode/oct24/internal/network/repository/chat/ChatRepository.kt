package com.getcode.oct24.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.Member
import com.getcode.oct24.data.Room
import com.getcode.oct24.data.RoomWithMemberCount
import com.getcode.oct24.data.RoomWithMembers
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.domain.model.query.QueryOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    suspend fun getChats(
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<Room>>

    suspend fun getChat(identifier: ChatIdentifier): Result<RoomWithMembers>
    suspend fun getChatMembers(identifier: ChatIdentifier): Result<List<Member>>
    suspend fun startChat(type: StartChatRequestType): Result<Room>
    suspend fun joinChat(identifier: ChatIdentifier): Result<RoomWithMembers>
    suspend fun leaveChat(chatId: ID): Result<Unit>
    suspend fun mute(chatId: ID): Result<Unit>
    suspend fun unmute(chatId: ID): Result<Unit>
    fun observeTyping(conversationId: ID): Flow<Boolean>
    fun openEventStream(coroutineScope: CoroutineScope)
    fun closeEventStream()

    // Self Defense Room Controls
    suspend fun removeUser(conversationId: ID, userId: ID): Result<Unit>
}