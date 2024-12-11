package xyz.flipchat.services.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.model.KinAmount
import com.getcode.utils.ErrorUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.Member
import xyz.flipchat.services.data.Room
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.MemberUpdate
import xyz.flipchat.services.domain.model.chat.db.ChatDbUpdate
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.data.mapper.LastMessageMapper
import xyz.flipchat.services.internal.data.mapper.MemberUpdateMapper
import xyz.flipchat.services.internal.data.mapper.MetadataRoomMapper
import xyz.flipchat.services.internal.data.mapper.RoomWithMembersMapper
import xyz.flipchat.services.internal.network.chat.ChatStreamUpdate
import xyz.flipchat.services.internal.network.service.ChatHomeStreamReference
import xyz.flipchat.services.internal.network.service.ChatService
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RealChatRepository @Inject constructor(
    private val userManager: UserManager,
    private val service: ChatService,
    private val roomMapper: MetadataRoomMapper,
    private val roomWithMembersMapper: RoomWithMembersMapper,
    private val conversationMapper: RoomConversationMapper,
    private val memberUpdateMapper: MemberUpdateMapper,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val lastMessageMapper: LastMessageMapper,
    private val messageMapper: ConversationMessageMapper,
): ChatRepository {
    private var homeStreamReference: ChatHomeStreamReference? = null
    private val _typingChats = MutableStateFlow<List<ID>>(emptyList())
    override val typingChats: StateFlow<List<ID>>
        get() = _typingChats.asStateFlow()

    override suspend fun getChats(
        queryOptions: QueryOptions,
    ): Result<List<Room>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.getChats(owner, queryOptions)
                .map { it.map { meta -> roomMapper.map(meta) } }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun getChat(identifier: ChatIdentifier): Result<RoomWithMembers> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.getChat(owner, identifier)
                .map { roomWithMembersMapper.map(it) }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun getChatMembers(identifier: ChatIdentifier): Result<List<Member>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.getChat(owner, identifier)
                .map { roomWithMembersMapper.map(it) }
                .map { it.members }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun startChat(type: StartChatRequestType): Result<RoomWithMembers> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.startChat(owner, type)
                .map { roomWithMembersMapper.map(it) }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun joinChat(identifier: ChatIdentifier, paymentId: ID?): Result<RoomWithMembers> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.joinChat(owner, identifier, paymentId)
                .map { roomWithMembersMapper.map(it) }
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun leaveChat(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.leaveChat(owner, chatId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun setDisplayName(chatId: ID, displayName: String): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.setDisplayName(owner, chatId, displayName)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun mute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.muteChat(owner, chatId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun unmute(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.unmuteChat(owner, chatId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun setCoverCharge(chatId: ID, amount: KinAmount): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))

        return withContext(Dispatchers.IO) {
            service.setCoverCharge(owner, chatId, amount)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override fun observeTyping(chatId: ID): Flow<Boolean> {
        return typingChats
            .map { chatId in it }
    }

    override fun openEventStream(coroutineScope: CoroutineScope, onEvent: (ChatDbUpdate) -> Unit) {
        val owner = userManager.keyPair ?: throw IllegalStateException("No keypair found for owner")
        val userId = userManager.userId ?: throw IllegalStateException("user not established")
        if (homeStreamReference == null) {
            homeStreamReference = service.openChatStream(coroutineScope, owner) { result ->
                if (result.isSuccess) {
                    val data = result.getOrNull() ?: return@openChatStream
                    val updates = data.mapNotNull { ChatStreamUpdate.invoke(it) }

                    println(updates.joinToString())
                    updates.onEach { update ->
                        // handle typing state changes
                        if (update.isTyping != null) {
                            if (update.isTyping) {
                                _typingChats.update { it + listOf(update.id).toSet() }
                            } else {
                                _typingChats.update { it - listOf(update.id).toSet() }
                            }
                        }

                        val memberUpdate = update.memberUpdate?.let {
                            memberUpdateMapper.map(it)
                        }

                        val updatedRoom = update.metadata?.let {
                            roomMapper.map(it)
                        }

                        val conversation = updatedRoom?.let { conversationMapper.map(it) }

                        // handle last message update
                        val message = if (userManager.openRoom != updatedRoom?.id) {
                            update.lastMessage?.let {
                                val chatId = update.id
                                val mapped = lastMessageMapper.map(userId to it)
                                messageMapper.map(chatId to mapped)
                            }
                        }  else {
                            null
                        }

                        val members = when (memberUpdate) {
                            is MemberUpdate.Refresh -> {
                                memberUpdate.members.map {
                                    conversationMemberMapper.map(
                                        Pair(
                                            update.id,
                                            it
                                        )
                                    )
                                }
                            }

                            null -> emptyList()
                        }

                        onEvent(
                            ChatDbUpdate(
                                conversation = conversation,
                                members = members,
                                message = message
                            )
                        )
                    }
                } else {
                    result.exceptionOrNull()?.let {
                        ErrorUtils.handleError(it)
                    }
                }
            }
        }
    }

    override fun closeEventStream() {
        homeStreamReference?.destroy()
        homeStreamReference = null
    }

    override suspend fun removeUser(chatId: ID, userId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return withContext(Dispatchers.IO) {
            service.removeUser(owner, chatId, userId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun reportUserForMessage(
        userId: ID,
        messageId: ID
    ): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return withContext(Dispatchers.IO) {
            service.reportUser(owner, userId, messageId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }

    override suspend fun muteUser(chatId: ID, userId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No ed25519 signature found for owner"))
        return withContext(Dispatchers.IO) {
            service.muteUser(owner, chatId, userId)
                .onFailure { ErrorUtils.handleError(it) }
        }
    }
}