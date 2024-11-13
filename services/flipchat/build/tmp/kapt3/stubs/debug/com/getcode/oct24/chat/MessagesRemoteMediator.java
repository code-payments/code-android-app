package com.getcode.oct24.chat;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0002\u0018\u00002\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0001B\'\u0012\u0010\u0010\u0004\u001a\f\u0012\u0004\u0012\u00020\u00060\u0005j\u0002`\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJ\u000e\u0010\u0011\u001a\u00020\u0012H\u0096@\u00a2\u0006\u0002\u0010\u0013J*\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u0016\u001a\u00020\u00172\u0012\u0010\u0018\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0019H\u0096@\u00a2\u0006\u0002\u0010\u001aR\u0018\u0010\u0004\u001a\f\u0012\u0004\u0012\u00020\u00060\u0005j\u0002`\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000f\u001a\n\u0012\u0004\u0012\u00020\u0010\u0018\u00010\u0005X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/getcode/oct24/chat/MessagesRemoteMediator;", "Landroidx/paging/RemoteMediator;", "", "Lcom/getcode/oct24/domain/model/chat/ConversationMessageWithContentAndMember;", "chatId", "", "", "Lcom/getcode/model/ID;", "repository", "Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;", "conversationMessageWithContentMapper", "Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;", "(Ljava/util/List;Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;)V", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "lastFetchedItems", "Lcom/getcode/model/chat/ChatMessage;", "initialize", "Landroidx/paging/RemoteMediator$InitializeAction;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "load", "Landroidx/paging/RemoteMediator$MediatorResult;", "loadType", "Landroidx/paging/LoadType;", "state", "Landroidx/paging/PagingState;", "(Landroidx/paging/LoadType;Landroidx/paging/PagingState;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
@kotlin.OptIn(markerClass = {androidx.paging.ExperimentalPagingApi.class})
final class MessagesRemoteMediator extends androidx.paging.RemoteMediator<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> {
    @org.jetbrains.annotations.NotNull()
    private final java.util.List<java.lang.Byte> chatId = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.messaging.MessagingRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper conversationMessageWithContentMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.db.FcAppDatabase db = null;
    @org.jetbrains.annotations.Nullable()
    private java.util.List<com.getcode.model.chat.ChatMessage> lastFetchedItems;
    
    public MessagesRemoteMediator(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.messaging.MessagingRepository repository, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper conversationMessageWithContentMapper) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object initialize(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super androidx.paging.RemoteMediator.InitializeAction> $completion) {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.Nullable()
    public java.lang.Object load(@org.jetbrains.annotations.NotNull()
    androidx.paging.LoadType loadType, @org.jetbrains.annotations.NotNull()
    androidx.paging.PagingState<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationMessageWithContentAndMember> state, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super androidx.paging.RemoteMediator.MediatorResult> $completion) {
        return null;
    }
}