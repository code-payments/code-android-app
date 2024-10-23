package com.getcode.oct24.network.controllers

import androidx.paging.PagingData
import com.getcode.oct24.domain.model.chat.Conversation
import com.getcode.oct24.domain.model.chat.ConversationMessageWithContent
import com.getcode.oct24.domain.model.chat.ConversationWithLastPointers
import com.getcode.model.ID
import com.getcode.model.SocialUser
import com.getcode.model.chat.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface ConversationController {
    fun observeConversation(id: ID): Flow<ConversationWithLastPointers?>
    suspend fun createConversation(identifier: ID, with: SocialUser): Conversation
    suspend fun getConversation(identifier: ID): ConversationWithLastPointers?
    suspend fun getOrCreateConversation(
        identifier: ID,
        with: SocialUser
    ): ConversationWithLastPointers

    fun openChatStream(scope: CoroutineScope, conversation: Conversation)
    fun closeChatStream()
    suspend fun hasInteracted(messageId: ID): Boolean
    suspend fun resetUnreadCount(conversationId: ID)
    suspend fun advanceReadPointer(conversationId: ID, messageId: ID, status: MessageStatus)
    suspend fun sendMessage(conversationId: ID, message: String): Result<ID>
    fun conversationPagingData(conversationId: ID): Flow<PagingData<ConversationMessageWithContent>>
    fun observeTyping(conversationId: ID): Flow<Boolean>
    suspend fun onUserStartedTypingIn(conversationId: ID)
    suspend fun onUserStoppedTypingIn(conversationId: ID)
}

//class ConversationStreamController @Inject constructor(
//    private val historyController: ChatHistoryController,
//    private val exchange: Exchange,
//    private val chatService: ChatServiceV2,
//    private val conversationMapper: ConversationMapper,
//    private val messageWithContentMapper: ConversationMessageWithContentMapper,
//    private val tipController: TipController,
//) : ConversationController {
//    private val pagingConfig = PagingConfig(pageSize = 20)
//
//    private val db: AppDatabase by lazy { Database.requireInstance() }
//
//    private var stream: ChatMessageStreamReference? = null
//
//    private val typingChats = MutableStateFlow<List<ID>>(emptyList())
//
//    private fun conversationPagingSource(conversationId: ID) =
//        db.conversationMessageDao().observeConversationMessages(conversationId.base58)
//
//    override fun observeConversation(id: ID): Flow<ConversationWithLastPointers?> {
//        return db.conversationDao().observeConversation(id)
//    }
//
//    override suspend fun createConversation(identifier: ID, with: SocialUser): Conversation {
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
//        val self = tipController.connectedAccount.value ?: throw IllegalStateException()
//        return chatService.startChat(owner, self, with, identifier, ChatType.TwoWay)
//            .onSuccess { historyController.addChat(it) }
//            .map { conversationMapper.map(it) }
//            .onSuccess {
//                // TODO: remove
//                // stop gap until startChat rejects created chats for the same identifier
//                db.conversationIntentMappingDao()
//                    .insert(
//                        ConversationIntentIdReference(
//                            conversationIdBase58 = it.id.base58,
//                            intentIdBase58 = identifier.base58
//                        )
//                    )
//                db.conversationDao().upsertConversations(it)
//            }
//            .getOrThrow()
//    }
//
//    override suspend fun getConversation(identifier: ID): ConversationWithLastPointers? {
//        val conversation = db.conversationDao().findConversation(identifier) ?: return null
//        return conversation
//    }
//
//    override suspend fun getOrCreateConversation(
//        identifier: ID,
//        with: SocialUser
//    ): ConversationWithLastPointers {
//        var conversationByChatId = getConversation(identifier)
//        if (conversationByChatId != null) {
//            return conversationByChatId
//        }
//
//        // lookup chat ID by tip intent ID
//        val conversationId = db.conversationIntentMappingDao().conversationIdByReference(identifier)
//        if (conversationId != null) {
//            conversationByChatId = getConversation(identifier)
//        }
//
//        if (conversationByChatId != null) {
//            return conversationByChatId
//        }
//
//        return ConversationWithLastPointers(createConversation(identifier, with), emptyList())
//    }
//
//    @Throws(IllegalStateException::class)
//    override fun openChatStream(scope: CoroutineScope, conversation: Conversation) {
//        runCatching { closeChatStream() }
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: throw IllegalStateException()
//
//        val chat = historyController.findChat { it.id == conversation.id }
//            ?: throw IllegalArgumentException("Unable to resolve chat for this conversation")
//        val memberId = chat.selfId ?: throw IllegalStateException("Not a member of this chat")
//
//        scope.launch(Dispatchers.IO) {
//            savePointers(conversationId = conversation.id,
//                pointers = chat.members.flatMap { member ->
//                    member.pointers.mapNotNull { ptr ->
//                        val messageId = ptr.messageId ?: return@mapNotNull null
//                        val id = ptr.memberId ?: return@mapNotNull null
//                        PointerStatus(
//                            memberId = id,
//                            messageId = messageId,
//                            messageStatus = when (ptr) {
//                                is Pointer.Delivered -> MessageStatus.Delivered
//                                is Pointer.Read -> MessageStatus.Read
//                                is Pointer.Sent -> MessageStatus.Sent
//                                is Pointer.Unknown -> MessageStatus.Unknown
//                            }
//                        )
//                    }
//                }
//            )
//        }
//
//        stream = chatService.openChatStream(
//            scope = scope,
//            conversation = conversation,
//            memberId = memberId,
//            owner = owner,
//            chatLookup = { chat }
//        ) result@{ result ->
//            if (result.isSuccess) {
//                val updates = result.getOrNull() ?: return@result
//                val (messages, pointers, isTyping) = updates
//
//                typingChats.value = if (isTyping) {
//                    typingChats.value + listOf(conversation.id).toSet()
//                } else {
//                    typingChats.value - listOf(conversation.id).toSet()
//                }
//
//                historyController.updateChatWithMessages(chat, messages)
//                val messagesWithContent = messages.map {
//                    messageWithContentMapper.map(chat.id to it)
//                }
//
//                val identityRevealed = messages
//                    .flatMap { it.contents }
//                    .filterIsInstance<MessageContent.IdentityRevealed>()
//                    .firstOrNull()
//                    .takeIf { chat.isConversation }
//
//                if (identityRevealed != null && conversation.members.isNotEmpty()) {
//                    val members = conversation.members.map {
//                        if (identityRevealed.memberId == it.id) {
//                            it.copy(identity = identityRevealed.identity)
//                        } else {
//                            it
//                        }
//                    }
//                    scope.launch(Dispatchers.IO) {
//                        db.conversationDao()
//                            .upsertConversations(
//                                conversation.copy(members = members)
//                            )
//                    }
//                }
//
//                trace(
//                    tag = "ConversationStream",
//                    message = "chat messages: ${messages.count()}, pointers=${pointers.count()}, isTyping=$isTyping",
//                    type = TraceType.Silent
//                )
//
//                scope.launch(Dispatchers.IO) {
//                    db.conversationMessageDao().upsertMessagesWithContent(messagesWithContent)
//                    savePointers(conversationId = conversation.id, pointers = pointers)
//                }
//            } else {
//                result.exceptionOrNull()?.let {
//                    ErrorUtils.handleError(it)
//                }
//            }
//        }
//    }
//
//    private suspend fun savePointers(
//        conversationId: ID,
//        pointers: List<PointerStatus>
//    ) {
//        pointers
//            .onEach {
//                db.conversationPointersDao().insert(
//                    conversationId = conversationId,
//                    messageId = it.messageId,
//                    status = it.messageStatus
//                )
//            }
//    }
//
//    override fun closeChatStream() {
//        stream?.destroy()
//    }
//
//    override suspend fun hasInteracted(messageId: ID): Boolean {
//        // TODO: will require conversation model update
//        return false
//    }
//
//    override suspend fun resetUnreadCount(conversationId: ID) {
//        val chat = historyController.findChat { it.id == conversationId } ?: return
//        historyController.resetUnreadCount(chat.id)
//    }
//
//    override suspend fun advanceReadPointer(
//        conversationId: ID,
//        messageId: ID,
//        status: MessageStatus
//    ) {
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return
//
//        val chat = historyController.findChat { it.id == conversationId } ?: return
//
//        val memberId = chat.selfId ?: return
//
//        chatService.advancePointer(owner, conversationId, memberId, messageId, status)
//            .onSuccess {
//                trace("advanced pointer for chat on ${messageId.description} => $status")
//            }.onFailure { it.printStackTrace() }
//    }
//
//    override suspend fun sendMessage(conversationId: ID, message: String): Result<ID> {
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair
//            ?: return Result.failure(Throwable("Owner not found"))
//
//        val chat = historyController.findChat { it.id == conversationId }
//            ?: return Result.failure(Throwable("Chat not found"))
//
//        return chatService.sendMessage(
//            owner = owner,
//            chat = chat,
//            content = OutgoingMessageContent.Text(message)
//        ).map {
//            val messageWithContent = messageWithContentMapper.map(conversationId to it)
//            CoroutineScope(Dispatchers.IO).launch {
//                db.conversationMessageDao().upsertMessagesWithContent(messageWithContent)
//            }
//
//            messageWithContent.message.id
//        }
//    }
//
//    override fun conversationPagingData(conversationId: ID) =
//        Pager(
//            config = pagingConfig,
//            initialKey = null,
//        ) { conversationPagingSource(conversationId) }.flow
//
//    override fun observeTyping(conversationId: ID): Flow<Boolean> =
//        typingChats.map { it.contains(conversationId) }
//
//    override suspend fun onUserStartedTypingIn(conversationId: ID) {
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return
//
//        val chat = historyController.findChat { it.id == conversationId }
//            ?: return
//
//        chatService.onStartedTyping(
//            owner, chat
//        ).onSuccess {
//            println("on typing started reported")
//        }.onFailure {
//            if (BuildConfig.DEBUG) {
//                it.printStackTrace()
//            }
//        }
//    }
//
//    override suspend fun onUserStoppedTypingIn(conversationId: ID) {
//        val owner = SessionManager.getOrganizer()?.ownerKeyPair ?: return
//
//        val chat = historyController.findChat { it.id == conversationId }
//            ?: return
//
//
//        chatService.onStoppedTyping(
//            owner, chat
//        ).onSuccess {
//            println("on typing stopped reported")
//        }.onFailure {
//            if (BuildConfig.DEBUG) {
//                it.printStackTrace()
//            }
//        }
//    }
//
//}