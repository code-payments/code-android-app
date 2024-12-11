package xyz.flipchat.services.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.model.KinAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.Member
import xyz.flipchat.services.data.Room
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.model.chat.db.ChatDbUpdate
import xyz.flipchat.services.domain.model.query.QueryOptions

interface ChatRepository {
    suspend fun getChats(
        queryOptions: QueryOptions = QueryOptions()
    ): Result<List<Room>>

    suspend fun getChat(identifier: ChatIdentifier): Result<RoomWithMembers>
    suspend fun getChatMembers(identifier: ChatIdentifier): Result<List<Member>>
    suspend fun startChat(type: StartChatRequestType): Result<RoomWithMembers>
    suspend fun joinChat(
        identifier: ChatIdentifier,
        paymentId: ID? = null,
    ): Result<RoomWithMembers>
    suspend fun leaveChat(chatId: ID): Result<Unit>
    suspend fun setDisplayName(chatId: ID, displayName: String): Result<Unit>
    suspend fun mute(chatId: ID): Result<Unit>
    suspend fun unmute(chatId: ID): Result<Unit>
    suspend fun setCoverCharge(chatId: ID, amount: KinAmount): Result<Unit>
    fun observeTyping(chatId: ID): Flow<Boolean>
    fun openEventStream(coroutineScope: CoroutineScope, onEvent: (ChatDbUpdate) -> Unit)
    fun closeEventStream()

    val typingChats: StateFlow<List<ID>>

    // Self Defense Room Controls
    suspend fun removeUser(chatId: ID, userId: ID): Result<Unit>
    suspend fun reportUserForMessage(userId: ID, messageId: ID): Result<Unit>
    suspend fun muteUser(chatId: ID, userId: ID): Result<Unit>
}