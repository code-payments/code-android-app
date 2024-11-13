package com.getcode.oct24.chat;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00a2\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u00002\u00020\u0001B/\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ:\u0010\u001b\u001a\u00020\u001c2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e2\u0010\u0010\u001f\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e2\u0006\u0010 \u001a\u00020!H\u0086@\u00a2\u0006\u0002\u0010\"J\u0006\u0010#\u001a\u00020\u001cJ&\u0010$\u001a\u000e\u0012\u0004\u0012\u00020&\u0012\u0004\u0012\u00020\'0%2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0002J@\u0010(\u001a\b\u0012\u0004\u0012\u00020\u001c0)2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e2\u0010\u0010\u001f\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b*\u0010+J \u0010,\u001a\u00020\u001c2\u0010\u0010-\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00a2\u0006\u0002\u0010.J\"\u0010/\u001a\u0004\u0018\u0001002\u0010\u0010-\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00a2\u0006\u0002\u0010.J.\u00101\u001a\b\u0012\u0004\u0012\u00020\u001c0)2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b2\u0010.J$\u00103\u001a\u000e\u0012\u0004\u0012\u00020&\u0012\u0004\u0012\u00020\'042\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eJ \u00105\u001a\n\u0012\u0006\u0012\u0004\u0018\u000100062\u0010\u00107\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eJ\u001e\u00108\u001a\b\u0012\u0004\u0012\u000209062\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eJ \u0010:\u001a\u00020\u001c2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00a2\u0006\u0002\u0010.J \u0010;\u001a\u00020\u001c2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00a2\u0006\u0002\u0010.J \u0010<\u001a\u00020\u001c2\u0006\u0010=\u001a\u00020>2\u0010\u0010-\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eJ@\u0010?\u001a\b\u0012\u0004\u0012\u00020\u001c0)2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e2\u0010\u0010@\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bA\u0010+J \u0010B\u001a\u00020\u001c2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001eH\u0086@\u00a2\u0006\u0002\u0010.J@\u0010C\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e0)2\u0010\u0010\u001d\u001a\f\u0012\u0004\u0012\u00020\u00180\u0017j\u0002`\u001e2\u0006\u0010D\u001a\u00020EH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bF\u0010GR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\r\u001a\u00020\u000e8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u0004\u00a2\u0006\u0002\n\u0000R#\u0010\u0015\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00180\u00170\u00170\u0016\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0019\u0010\u001aR\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006H"}, d2 = {"Lcom/getcode/oct24/chat/RoomController;", "", "chatRepository", "Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;", "messagingRepository", "Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "conversationMemberMapper", "Lcom/getcode/oct24/internal/data/mapper/ConversationMemberMapper;", "conversationMessageWithContentMapper", "Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;", "(Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;Lcom/getcode/oct24/user/UserManager;Lcom/getcode/oct24/internal/data/mapper/ConversationMemberMapper;Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;)V", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "getDb", "()Lcom/getcode/oct24/internal/db/FcAppDatabase;", "db$delegate", "Lkotlin/Lazy;", "pagingConfig", "Landroidx/paging/PagingConfig;", "typingChats", "Lkotlinx/coroutines/flow/StateFlow;", "", "", "getTypingChats", "()Lkotlinx/coroutines/flow/StateFlow;", "advancePointer", "", "conversationId", "Lcom/getcode/model/ID;", "messageId", "status", "Lcom/getcode/model/chat/MessageStatus;", "(Ljava/util/List;Ljava/util/List;Lcom/getcode/model/chat/MessageStatus;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "closeMessageStream", "conversationPagingSource", "Landroidx/paging/PagingSource;", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContentAndMember;", "deleteMessage", "Lkotlin/Result;", "deleteMessage-0E7RQCE", "(Ljava/util/List;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getChatMembers", "identifier", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getConversation", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastPointers;", "leaveRoom", "leaveRoom-gIAlu-s", "messages", "Landroidx/paging/Pager;", "observeConversation", "Lkotlinx/coroutines/flow/Flow;", "id", "observeTyping", "", "onUserStartedTypingIn", "onUserStoppedTypingIn", "openMessageStream", "scope", "Lkotlinx/coroutines/CoroutineScope;", "removeUser", "userId", "removeUser-0E7RQCE", "resetUnreadCount", "sendMessage", "message", "", "sendMessage-0E7RQCE", "(Ljava/util/List;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class RoomController {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.chat.ChatRepository chatRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.messaging.MessagingRepository messagingRepository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.ConversationMemberMapper conversationMemberMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper conversationMessageWithContentMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy db$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final androidx.paging.PagingConfig pagingConfig = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<java.util.List<java.lang.Byte>>> typingChats = null;
    
    @javax.inject.Inject()
    public RoomController(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.chat.ChatRepository chatRepository, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.messaging.MessagingRepository messagingRepository, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ConversationMemberMapper conversationMemberMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper conversationMessageWithContentMapper) {
        super();
    }
    
    private final com.getcode.oct24.internal.db.FcAppDatabase getDb() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> observeConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> id) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getConversation(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> identifier, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastPointers> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getChatMembers(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> identifier, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void openMessageStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope scope, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> identifier) {
    }
    
    public final void closeMessageStream() {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object resetUnreadCount(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object advancePointer(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> messageId, @org.jetbrains.annotations.NotNull()
    com.getcode.model.chat.MessageStatus status, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final androidx.paging.PagingSource<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> conversationPagingSource(java.util.List<java.lang.Byte> conversationId) {
        return null;
    }
    
    @kotlin.OptIn(markerClass = {androidx.paging.ExperimentalPagingApi.class})
    @org.jetbrains.annotations.NotNull()
    public final androidx.paging.Pager<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> messages(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<java.util.List<java.lang.Byte>>> getTypingChats() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<java.lang.Boolean> observeTyping(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object onUserStartedTypingIn(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object onUserStoppedTypingIn(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
}