package com.getcode.oct24.network.controllers;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000n\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0006\u0010\u0016\u001a\u00020\u0017J.\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u001f\u0010 JB\u0010!\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\n\b\u0002\u0010\"\u001a\u0004\u0018\u00010#2\u0018\b\u0002\u0010$\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e0\u001cH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b%\u0010&J.\u0010\'\u001a\b\u0012\u0004\u0012\u00020(0\u00192\u0010\u0010)\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b*\u0010 J$\u0010\'\u001a\b\u0012\u0004\u0012\u00020(0\u00192\u0006\u0010+\u001a\u00020,H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b*\u0010-J$\u0010.\u001a\b\u0012\u0004\u0012\u00020(0\u00192\u0006\u0010+\u001a\u00020,H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b/\u0010-J\u000e\u00100\u001a\u00020\u00172\u0006\u00101\u001a\u000202R-\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n0\b8FX\u0086\u0084\u0002\u00a2\u0006\u0012\n\u0004\b\u000f\u0010\u0010\u0012\u0004\b\u000b\u0010\f\u001a\u0004\b\r\u0010\u000eR\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0011\u001a\u00020\u00128BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0015\u0010\u0010\u001a\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u00063"}, d2 = {"Lcom/getcode/oct24/network/controllers/ChatsController;", "", "conversationMapper", "Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;", "repository", "Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;", "(Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;)V", "chats", "Landroidx/paging/Pager;", "", "Lcom/getcode/oct24/domain/model/chat/ConversationWithMembersAndLastMessage;", "getChats$annotations", "()V", "getChats", "()Landroidx/paging/Pager;", "chats$delegate", "Lkotlin/Lazy;", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "getDb", "()Lcom/getcode/oct24/internal/db/FcAppDatabase;", "db$delegate", "closeEventStream", "", "createDirectMessage", "Lkotlin/Result;", "Lcom/getcode/oct24/data/Room;", "recipient", "", "", "Lcom/getcode/model/ID;", "createDirectMessage-gIAlu-s", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "createGroup", "title", "", "participants", "createGroup-0E7RQCE", "(Ljava/lang/String;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "joinRoom", "Lcom/getcode/oct24/data/RoomWithMembers;", "roomId", "joinRoom-gIAlu-s", "roomNumber", "", "(JLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "lookupRoom", "lookupRoom-gIAlu-s", "openEventStream", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "flipchat_debug"})
public final class ChatsController {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.chat.ChatRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy db$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy chats$delegate = null;
    
    @javax.inject.Inject()
    public ChatsController(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.chat.ChatRepository repository) {
        super();
    }
    
    private final com.getcode.oct24.internal.db.FcAppDatabase getDb() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final androidx.paging.Pager<java.lang.Integer, com.getcode.oct24.domain.model.chat.ConversationWithMembersAndLastMessage> getChats() {
        return null;
    }
    
    @kotlin.OptIn(markerClass = {androidx.paging.ExperimentalPagingApi.class})
    @java.lang.Deprecated()
    public static void getChats$annotations() {
    }
    
    public final void openEventStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope coroutineScope) {
    }
    
    public final void closeEventStream() {
    }
}