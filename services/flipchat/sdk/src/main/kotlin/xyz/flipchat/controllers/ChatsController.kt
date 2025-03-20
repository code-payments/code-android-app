package xyz.flipchat.controllers

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.room.withTransaction
import com.getcode.model.ID
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.TraceType
import com.getcode.utils.base58
import com.getcode.utils.trace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.flipchat.chat.paging.ChatsPagingSource
import xyz.flipchat.chat.paging.ChatsRemoteMediator
import xyz.flipchat.internal.db.FcAppDatabase
import xyz.flipchat.services.data.ChatIdentifier
import xyz.flipchat.services.data.RoomWithMembers
import xyz.flipchat.services.data.StartChatRequestType
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.ConversationMessage
import xyz.flipchat.services.domain.model.chat.ConversationWithMembersAndLastMessage
import xyz.flipchat.services.domain.model.chat.db.ConversationMemberUpdate
import xyz.flipchat.services.domain.model.chat.db.ConversationUpdate
import xyz.flipchat.services.domain.model.query.QueryOptions
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.data.mapper.UserMapper
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatsController @Inject constructor(
    private val conversationMapper: RoomConversationMapper,
    private val conversationMemberMapper: ConversationMemberMapper,
    private val userMapper: UserMapper,
    private val conversationMessageMapper: ConversationMessageMapper,
    private val chatRepository: ChatRepository,
    private val messagingRepository: MessagingRepository,
    private val userManager: UserManager,
    private val resources: ResourceHelper,
) {
    private val db: FcAppDatabase
        get() = FcAppDatabase.requireInstance()

    private val pagingConfig = PagingConfig(pageSize = 20)

    @OptIn(ExperimentalPagingApi::class)
    val chats: Pager<Int, ConversationWithMembersAndLastMessage> by lazy {
        Pager(
            config = pagingConfig,
            remoteMediator = ChatsRemoteMediator(chatRepository, conversationMapper)
        ) {
            ChatsPagingSource(db)
        }
    }

    suspend fun updateRooms() = coroutineScope {
        chatRepository.getChats()
            .onSuccess { rooms ->
                // remove rooms no longer apart of
                db.conversationDao().purgeConversationsNotIn(rooms.map { it.id })
                rooms.map { room ->
                    async { updateRoom(room.id) }
                }.forEach { it.await() }
            }
    }

    suspend fun updateRoom(roomId: ID) {
        coroutineScope {
            launch {
                chatRepository.getChat(ChatIdentifier.Id(roomId))
                    .onSuccess { (room, members) ->
                        db.withTransaction {
                            withContext(Dispatchers.IO) {
                                db.conversationDao().upsertConversations(conversationMapper.map(room))
                                members.map { conversationMemberMapper.map(room.id to it) }.let {
                                    db.conversationMembersDao().upsertMembers(*it.toTypedArray())
                                }
                                members.map { userMapper.map(it) }.let {
                                    db.userDao().upsert(*it.toTypedArray())
                                }

                                members.map {
                                    val socialProfiles = it.identity?.socialProfiles
                                    if (socialProfiles != null) {
                                        db.userSocialDao().upsert(it.id, socialProfiles)
                                    }
                                }
                            }
                        }
                    }
            }

            launch {
                syncMessagesFromLast(conversationId = roomId)
            }
        }
    }

    fun openEventStream(coroutineScope: CoroutineScope) {
        runCatching {
            chatRepository.openEventStream(coroutineScope) { event ->
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {

                        db.withTransaction {
                            event.metadata.onEach { update ->
                                when (update) {
                                    is ConversationUpdate.CoverCharge -> {
                                        db.conversationDao().updateMessagingFee(update.roomId, update.amount)
                                    }
                                    is ConversationUpdate.DisplayName -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao()
                                                .setDisplayName(update.roomId, update.name)
                                        }
                                    }
                                    is ConversationUpdate.LastActivity -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao().upsertConversations(
                                                conversation.copy(lastActivity = update.timestamp)
                                            )
                                        }
                                    }
                                    is ConversationUpdate.Refresh -> db.conversationDao().upsertConversations(update.conversation)
                                    is ConversationUpdate.UnreadCount -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao().upsertConversations(
                                                conversation.copy(unreadCount = update.numUnread, hasMoreUnread = update.hasMoreUnread)
                                            )
                                        }
                                    }

                                    is ConversationUpdate.OpenStatus -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao().upsertConversations(
                                                conversation.copy(isOpen = update.nowOpen)
                                            )
                                        }
                                    }

                                    is ConversationUpdate.Description -> {
                                        val conversation = db.conversationDao().findConversationRaw(update.roomId)
                                        if (conversation != null) {
                                            db.conversationDao()
                                                .setDescription(update.roomId, update.description)
                                        }
                                    }
                                }
                            }
                        }

                        db.withTransaction {
                            event.members.onEach { update ->
                                when (update) {
                                    is ConversationMemberUpdate.FullRefresh -> {
                                        val members = update.members.map {
                                            conversationMemberMapper.map(
                                                Pair(
                                                    update.roomId,
                                                    it
                                                )
                                            )
                                        }
                                        val users = update.members.map {
                                            userMapper.map(it)
                                        }

                                        db.conversationMembersDao()
                                            .upsertMembers(*members.toTypedArray())
                                        db.userDao()
                                            .upsert(*users.toTypedArray())

                                        update.members.onEach {
                                            val member = conversationMemberMapper.map(Pair(update.roomId, it))
                                            val user = userMapper.map(it)
                                            val socialProfiles = it.identity?.socialProfiles

                                            db.conversationMembersDao().upsertMembers(member)
                                            db.userDao().upsert(user)
                                            if (socialProfiles != null) {
                                                db.userSocialDao().upsert(it.id, socialProfiles)
                                            }
                                        }
                                    }

                                    is ConversationMemberUpdate.IndividualRefresh -> {
                                        val member = conversationMemberMapper.map(
                                            Pair(update.roomId, update.member)
                                        )

                                        val user = userMapper.map(update.member)
                                        db.conversationMembersDao().upsertMembers(member)
                                        db.userDao().upsert(user)

                                        val socialProfiles = update.member.identity?.socialProfiles
                                        if (socialProfiles != null) {
                                            db.userSocialDao().upsert(
                                                member.id,
                                                socialProfiles
                                            )
                                        }
                                    }

                                    is ConversationMemberUpdate.Joined -> {
                                        val member = conversationMemberMapper.map(
                                            Pair(update.roomId, update.member)
                                        )

                                        val user = userMapper.map(update.member)
                                        db.conversationMembersDao().upsertMembers(member)
                                        db.userDao().upsert(user)

                                        val socialProfiles = update.member.identity?.socialProfiles
                                        if (socialProfiles != null) {
                                            db.userSocialDao().upsert(
                                                member.id,
                                                socialProfiles
                                            )
                                        }
                                    }

                                    is ConversationMemberUpdate.Left -> {
                                        db.conversationMembersDao().removeMemberFromConversation(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }

                                    is ConversationMemberUpdate.Muted -> {
                                        db.conversationMembersDao().muteMember(
                                            conversationId = update.roomId,
                                            memberId = update.memberId
                                        )
                                    }

                                    is ConversationMemberUpdate.Removed -> {
                                        db.conversationMembersDao().removeMemberFromConversation(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }

                                    is ConversationMemberUpdate.Demoted -> {
                                        db.conversationMembersDao().demoteMember(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }
                                    is ConversationMemberUpdate.Promoted -> {
                                        db.conversationMembersDao().promoteMember(
                                            memberId = update.memberId,
                                            conversationId = update.roomId
                                        )
                                    }

                                    is ConversationMemberUpdate.IdentityChanged -> {
                                        db.userDao().updateIdentity(
                                            update.memberId,
                                            update.identity
                                        )

                                        db.userSocialDao().upsert(
                                            update.memberId,
                                            update.identity.socialProfiles
                                        )
                                    }
                                }
                            }
                        }
                    }

                    event.message?.let { newMessage ->
                        syncMessagesFromLast(newMessage.conversationId, newMessage)
                    }
                }
            }
        }
    }

    private suspend fun syncMessagesFromLast(
        conversationId: ID,
        newMessage: ConversationMessage? = null
    ) {
        var token: ID?
        if (newMessage != null) {
            // sync between last in DB and this message
            val newestInDb =
                db.conversationMessageDao().getNewestMessage(conversationId)
            if (newestInDb?.id == newMessage.id) {
                withContext(Dispatchers.IO) {
                    db.conversationMessageDao().upsertMessages(
                        conversationId,
                        listOf(newMessage),
                        userManager.userId
                    )
                }
                return
            }

            token = newestInDb?.id
        } else {
            val newestInDb =
                db.conversationMessageDao().getNewestMessage(conversationId)
            token = newestInDb?.id
        }

        while (true) {
            val query = QueryOptions(token = token, descending = false, limit = 1_000)
            messagingRepository.getMessages(conversationId, query)
                .onSuccess { syncedMessages ->
                    trace(
                        "synced ${syncedMessages.count()} missing messages for ${conversationId.base58}",
                        type = TraceType.Silent
                    )
                    val messagesWithContent = syncedMessages.map {
                        conversationMessageMapper.map(conversationId to it)
                    }

                    withContext(Dispatchers.IO) {
                        db.conversationMessageDao().upsertMessages(
                            conversationId,
                            messagesWithContent,
                            userManager.userId
                        )
                    }

                    val nextToken =
                        db.conversationMessageDao().getNewestMessage(conversationId)?.id
                    if (nextToken == token || messagesWithContent.isEmpty()) {
                        return
                    }

                    token = nextToken
                }
                .onFailure {
                    if (newMessage != null) {
                        withContext(Dispatchers.IO) {
                            db.conversationMessageDao().upsertMessages(
                                conversationId,
                                listOf(newMessage),
                                userManager.userId
                            )
                        }
                    }
                    return
                }
        }
    }

    fun closeEventStream() {
        runCatching {
            chatRepository.closeEventStream()
        }
    }

    suspend fun lookupRoom(roomNumber: Long): Result<RoomWithMembers> {
        return chatRepository.getChat(identifier = ChatIdentifier.RoomNumber(roomNumber))
    }

    suspend fun lookupRoom(id: ID): Result<RoomWithMembers> {
        return chatRepository.getChat(identifier = ChatIdentifier.Id(id))
    }


    suspend fun createDirectMessage(recipient: ID): Result<RoomWithMembers> {
        return chatRepository.startChat(StartChatRequestType.TwoWay(recipient))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                val users = result.members.map { userMapper.map(it) }
                val socials = result.members.mapNotNull {
                    val profiles = it.identity?.socialProfiles ?: return@mapNotNull null
                    it.id to profiles
                }

                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                        db.userDao().upsert(*users.toTypedArray())
                        db.userSocialDao().upsert(*socials.toTypedArray())
                    }
                }
            }
    }

    suspend fun createGroup(
        title: String? = null,
        participants: List<ID> = emptyList(),
        paymentId: ID,
    ): Result<RoomWithMembers> {
        return chatRepository.startChat(StartChatRequestType.Group(title, participants, paymentId))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                val users = result.members.map { userMapper.map(it) }
                val socials = result.members.mapNotNull {
                    val profiles = it.identity?.socialProfiles ?: return@mapNotNull null
                    it.id to profiles
                }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                        db.userDao().upsert(*users.toTypedArray())
                        db.userSocialDao().upsert(*socials.toTypedArray())
                    }
                }
            }
    }

    suspend fun joinRoomAsSpectator(roomId: ID): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.Id(roomId))
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                val users = result.members.map { userMapper.map(it) }
                val socials = result.members.mapNotNull {
                    val profiles = it.identity?.socialProfiles ?: return@mapNotNull null
                    it.id to profiles
                }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                        db.userDao().upsert(*users.toTypedArray())
                        db.userSocialDao().upsert(*socials.toTypedArray())
                    }
                }
            }
    }

    suspend fun joinRoomAsFullMember(roomId: ID, paymentId: ID?): Result<RoomWithMembers> {
        return chatRepository.joinChat(ChatIdentifier.Id(roomId), paymentId)
            .onSuccess { result ->
                val members =
                    result.members.map { conversationMemberMapper.map(result.room.id to it) }
                val users = result.members.map { userMapper.map(it) }
                val socials = result.members.mapNotNull {
                    val profiles = it.identity?.socialProfiles ?: return@mapNotNull null
                    it.id to profiles
                }
                db.withTransaction {
                    withContext(Dispatchers.IO) {
                        db.conversationDao()
                            .upsertConversations(conversationMapper.map(result.room))
                        db.conversationMembersDao().upsertMembers(*members.toTypedArray())
                        db.userDao().upsert(*users.toTypedArray())
                        db.userSocialDao().upsert(*socials.toTypedArray())
                    }
                }
            }
    }

    suspend fun checkDisplayNameForRoom(name: String): Result<Unit> {
        return chatRepository.checkDisplayName(name)
    }

    suspend fun muteRoom(roomId: ID): Result<Unit> {
        return chatRepository.mute(roomId)
            .onSuccess { db.conversationDao().muteChat(roomId) }
    }

    suspend fun unmuteRoom(roomId: ID): Result<Unit> {
        return chatRepository.unmute(roomId)
            .onSuccess { db.conversationDao().unmuteChat(roomId) }
    }
}