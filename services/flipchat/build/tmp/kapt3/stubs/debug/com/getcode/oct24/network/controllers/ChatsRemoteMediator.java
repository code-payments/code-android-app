package com.getcode.oct24.network.controllers;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0002\u0018\u00002\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0001B\u0015\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u000e\u0010\u000e\u001a\u00020\u000fH\u0096@\u00a2\u0006\u0002\u0010\u0010J*\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0012\u0010\u0015\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0016H\u0096@\u00a2\u0006\u0002\u0010\u0017R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000b\u001a\n\u0012\u0004\u0012\u00020\r\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0018"}, d2 = {"Lcom/getcode/oct24/network/controllers/ChatsRemoteMediator;", "Landroidx/paging/RemoteMediator;", "", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastMessage;", "repository", "Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;", "conversationMapper", "Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;", "(Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;)V", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "lastFetchedItems", "", "Lcom/getcode/oct24/data/Room;", "initialize", "Landroidx/paging/RemoteMediator$InitializeAction;", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "load", "Landroidx/paging/RemoteMediator$MediatorResult;", "loadType", "Landroidx/paging/LoadType;", "state", "Landroidx/paging/PagingState;", "(Landroidx/paging/LoadType;Landroidx/paging/PagingState;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
@kotlin.OptIn(markerClass = {androidx.paging.ExperimentalPagingApi.class})
final class ChatsRemoteMediator extends androidx.paging.RemoteMediator<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage> {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.chat.ChatRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.db.FcAppDatabase db = null;
    @org.jetbrains.annotations.Nullable()
    private java.util.List<com.getcode.oct24.data.Room> lastFetchedItems;
    
    public ChatsRemoteMediator(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.chat.ChatRepository repository, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper) {
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
    androidx.paging.PagingState<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage> state, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super androidx.paging.RemoteMediator.MediatorResult> $completion) {
        return null;
    }
}