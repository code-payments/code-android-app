package com.getcode.oct24.internal.network.repository.chat

import com.getcode.model.ID
import com.getcode.oct24.data.ChatIdentifier
import com.getcode.oct24.data.Member
import com.getcode.oct24.data.Room
import com.getcode.oct24.data.RoomWithMemberCount
import com.getcode.oct24.data.StartChatRequestType
import com.getcode.oct24.domain.mapper.RoomConversationMapper
import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.domain.model.chat.MemberUpdate
import com.getcode.oct24.domain.model.query.QueryOptions
import com.getcode.oct24.internal.data.mapper.ConversationMemberMapper
import com.getcode.oct24.internal.data.mapper.LastMessageMapper
import com.getcode.oct24.internal.data.mapper.MemberUpdateMapper
import com.getcode.oct24.internal.data.mapper.MetadataRoomMapper
import com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper
import com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper
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
    private val roomMapper: MetadataRoomMapper,
    private val roomWithMemberCountMapper: RoomWithMemberCountMapper,
    private val roomWithMembersMapper: RoomWithMembersMapper,
    private val conversationMapper: RoomConversationMapper,
    private val memberUpdateMapper: MemberUpdateMapper,
    private val conversationMemberMapper: ConversationMemberMapper,
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

        return service.getChats(owner, queryOptions)
            .map { it.map { meta -> roomMapper.map(meta) } }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getChat(identifier: ChatIdentifier): Result<RoomWithMemberCount> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.getChat(owner, identifier)
            .map { roomWithMemberCountMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun getChatMembers(identifier: ChatIdentifier): Result<List<Member>> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.getChat(owner, identifier)
            .map { roomWithMembersMapper.map(it) }
            .map { it.members }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun startChat(type: StartChatRequestType): Result<Room> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.startChat(owner, type)
            .map { roomMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun joinChat(identifier: ChatIdentifier): Result<RoomWithMemberCount> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.joinChat(owner, identifier)
            .map { roomWithMemberCountMapper.map(it) }
            .onFailure { ErrorUtils.handleError(it) }
    }

    override suspend fun leaveChat(chatId: ID): Result<Unit> {
        val owner = userManager.keyPair ?: return Result.failure(IllegalStateException("No keypair found for owner"))

        return service.leaveChat(owner, chatId)
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
        if (homeStreamReference == null) {
            homeStreamReference = service.openChatStream(coroutineScope, owner) { result ->
                if (result.isSuccess) {
                    val data = result.getOrNull()
                    val update = ChatStreamUpdate.invoke(data) ?: return@openChatStream

                    // handle typing state changes
                    if (update.isTyping != null) {
                        if (update.isTyping) {
                            typingChats.value + listOf(update.id).toSet()
                        } else {
                            typingChats.value - listOf(update.id).toSet()
                        }
                    }

                    val memberUpdate = update.memberUpdate?.let {
                        memberUpdateMapper.map(it)
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

                        if (memberUpdate != null) {
                            when (memberUpdate) {
                                is MemberUpdate.Refresh -> {
                                    val members = memberUpdate.members.map {
                                        conversationMemberMapper.map(
                                            Pair(
                                                update.id,
                                                it
                                            )
                                        )
                                    }
                                    db.conversationMembersDao().refreshMembers(update.id, members)
                                    db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                                }
                            }
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
    }

    override fun closeEventStream() {
        homeStreamReference?.destroy()
        homeStreamReference = null
    }

    override suspend fun removeUser(conversationId: ID, userId: ID): Result<Unit> {
        TODO("Not yet implemented")
    }
}