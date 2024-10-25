package com.getcode.oct24.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.Room
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.domain.mapper.ConversationMapper
import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.data.mapper.LastMessageMapper
import com.getcode.oct24.internal.data.mapper.RoomMapper
import com.getcode.oct24.internal.db.FcAppDatabase
import com.getcode.oct24.internal.network.model.chat.ChatStreamUpdate
import com.getcode.oct24.internal.network.service.ChatHomeStreamReference
import com.getcode.oct24.internal.network.service.ChatService
import com.getcode.oct24.user.UserManager
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealChatRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: ChatService,
    private val roomMapper: RoomMapper,
    private val conversationMapper: ConversationMapper,
    private val messageMapper: LastMessageMapper,
    private val messageWithContentMapper: ConversationMessageWithContentMapper,
): ChatRepository {
    private val db: FcAppDatabase by lazy { FcAppDatabase.requireInstance() }
    private var homeStreamReference: ChatHomeStreamReference? = null
    private val typingChats = MutableStateFlow<List<ID>>(emptyList())

    override suspend fun getChats(
        queryOptions: QueryOptions,
    ): Result<List<Room>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.getChats(owner, userId, queryOptions)
            .map { it.map { meta -> roomMapper.map(meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getChat(identifier: ChatIdentifier): Result<Room> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.getChat(owner, identifier)
            .map { roomMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun startChat(type: StartChatRequestType): Result<Room> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.startChat(owner, userId, type)
            .map { roomMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun joinChat(identifier: ChatIdentifier): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.joinChat(owner, userId, identifier)
    }

    override suspend fun leaveChat(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))
        val userId = userManager.userId ?: return Result.failure(IllegalStateException("No userId found for owner"))

        return service.leaveChat(owner, userId, chatId)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun mute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.setMuteState(owner, chatId, true)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun unmute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.setMuteState(owner, chatId, false)
            .onFailure { ErrorUtils.handleError(it) }
    }

    override fun observeTyping(conversationId: ID): Flow<Boolean> =
        typingChats.map { it.contains(conversationId) }

    override fun openEventStream(coroutineScope: CoroutineScope) {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("No userId found for owner")

        homeStreamReference = service.openChatStream(coroutineScope, owner, userId) { result ->
            if (result.isSuccess) {
                val data = result.getOrNull()
                val update = ChatStreamUpdate.invoke(data) ?: return@openChatStream

                // handle typing state changes
                if (update.isTyping != null && update.id != null) {
                    if (update.isTyping) {
                        typingChats.value + listOf(update.id).toSet()
                    } else {
                        typingChats.value - listOf(update.id).toSet()
                    }
                }

                val updatedChat = update.chat?.let {
                    roomMapper.map(it)
                }

                val conversation = updatedChat?.let { conversationMapper.map(it) }

                // handle last message update
                val message = update.lastMessage?.let {
                    val chatId = update.id ?: return@let null
                    val mapped = messageMapper.map(it)
                    messageWithContentMapper.map(chatId to mapped)
                }

                // update conversation if metadata changed
                coroutineScope.launch(Dispatchers.IO) {
                    if (conversation != null) {
                        db.conversationDao().upsertConversations(conversation)
                    }
                    if (message != null) {
                        db.conversationMessageDao().upsertMessagesWithContent(message)
                    }
                }
            } else {
                result.exceptionOrNull()?.let {
                    ErrorUtils.handleError(it)
                }
            }
        }
    }

    override fun closeEventStream() {
        homeStreamReference?.destroy()
    }
}