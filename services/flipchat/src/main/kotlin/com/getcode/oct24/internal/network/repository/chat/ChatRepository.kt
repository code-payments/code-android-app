package com.getcode.oct24.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.oct24.internal.data.mapper.RoomMapper
import com.getcode.oct24.internal.network.service.ChatService
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.Room
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.oct24.domain.mapper.ConversationMapper
import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.data.mapper.LastMessageMapper
import com.getcode.oct24.internal.network.model.chat.ChatStreamUpdate
import com.getcode.oct24.user.UserManager
import com.getcode.services.observers.BidirectionalStreamReference
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    suspend fun getChats(
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<Room>>

    suspend fun getChat(identifier: ChatIdentifier): Result<Room>
    suspend fun startChat(type: StartChatRequestType): Result<Room>
    suspend fun joinChat(identifier: ChatIdentifier): Result<Unit>
    suspend fun leaveChat(chatId: ID): Result<Unit>
    suspend fun mute(chatId: ID): Result<Unit>
    suspend fun unmute(chatId: ID): Result<Unit>
    fun observeTyping(conversationId: ID): Flow<Boolean>
    fun openEventStream(coroutineScope: CoroutineScope)
    fun closeEventStream()
}