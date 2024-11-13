package com.getcode.oct24.internal.network.repository.chat;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u00c6\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0001\u0018\u00002\u00020\u0001BW\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u0012\u0006\u0010\f\u001a\u00020\r\u0012\u0006\u0010\u000e\u001a\u00020\u000f\u0012\u0006\u0010\u0010\u001a\u00020\u0011\u0012\u0006\u0010\u0012\u001a\u00020\u0013\u0012\u0006\u0010\u0014\u001a\u00020\u0015\u00a2\u0006\u0002\u0010\u0016J\b\u0010+\u001a\u00020,H\u0016J$\u0010-\u001a\b\u0012\u0004\u0012\u00020/0.2\u0006\u00100\u001a\u000201H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b2\u00103J*\u00104\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002050\u00190.2\u0006\u00100\u001a\u000201H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b6\u00103J*\u00107\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002080\u00190.2\u0006\u00109\u001a\u00020:H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b;\u0010<J$\u0010=\u001a\b\u0012\u0004\u0012\u00020/0.2\u0006\u00100\u001a\u000201H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b>\u00103J.\u0010?\u001a\b\u0012\u0004\u0012\u00020,0.2\u0010\u0010@\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bA\u0010BJ.\u0010C\u001a\b\u0012\u0004\u0012\u00020,0.2\u0010\u0010@\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bD\u0010BJ \u0010E\u001a\b\u0012\u0004\u0012\u00020G0F2\u0010\u0010H\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(H\u0016J\u0010\u0010I\u001a\u00020,2\u0006\u0010J\u001a\u00020KH\u0016J@\u0010L\u001a\b\u0012\u0004\u0012\u00020,0.2\u0010\u0010H\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(2\u0010\u0010M\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bN\u0010OJ$\u0010P\u001a\b\u0012\u0004\u0012\u0002080.2\u0006\u0010Q\u001a\u00020RH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bS\u0010TJ.\u0010U\u001a\b\u0012\u0004\u0012\u00020,0.2\u0010\u0010@\u001a\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\bV\u0010BR \u0010\u0017\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001a0\u00190\u00190\u0018X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u001b\u001a\u00020\u001c8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001f\u0010 \u001a\u0004\b\u001d\u0010\u001eR\"\u0010!\u001a\u0016\u0012\u0004\u0012\u00020#\u0012\u0004\u0012\u00020$\u0018\u00010\"j\u0004\u0018\u0001`%X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0015X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R*\u0010&\u001a\u0018\u0012\u0014\u0012\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\u001a0\u0019j\u0002`(0\u00190\'8VX\u0096\u0004\u00a2\u0006\u0006\u001a\u0004\b)\u0010*R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006W"}, d2 = {"Lcom/getcode/oct24/internal/network/repository/chat/RealChatRepository;", "Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "service", "Lcom/getcode/oct24/internal/network/service/ChatService;", "roomMapper", "Lcom/getcode/oct24/internal/data/mapper/MetadataRoomMapper;", "roomWithMemberCountMapper", "Lcom/getcode/oct24/internal/data/mapper/RoomWithMemberCountMapper;", "roomWithMembersMapper", "Lcom/getcode/oct24/internal/data/mapper/RoomWithMembersMapper;", "conversationMapper", "Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;", "memberUpdateMapper", "Lcom/getcode/oct24/internal/data/mapper/MemberUpdateMapper;", "conversationMemberMapper", "Lcom/getcode/oct24/internal/data/mapper/ConversationMemberMapper;", "messageMapper", "Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;", "messageWithContentMapper", "Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;", "(Lcom/getcode/oct24/user/UserManager;Lcom/getcode/oct24/internal/network/service/ChatService;Lcom/getcode/oct24/internal/data/mapper/MetadataRoomMapper;Lcom/getcode/oct24/internal/data/mapper/RoomWithMemberCountMapper;Lcom/getcode/oct24/internal/data/mapper/RoomWithMembersMapper;Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;Lcom/getcode/oct24/internal/data/mapper/MemberUpdateMapper;Lcom/getcode/oct24/internal/data/mapper/ConversationMemberMapper;Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;)V", "_typingChats", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "getDb", "()Lcom/getcode/oct24/internal/db/FcAppDatabase;", "db$delegate", "Lkotlin/Lazy;", "homeStreamReference", "Lcom/getcode/services/observers/BidirectionalStreamReference;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsRequest;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsResponse;", "Lcom/getcode/oct24/internal/network/service/ChatHomeStreamReference;", "typingChats", "Lkotlinx/coroutines/flow/StateFlow;", "Lcom/getcode/model/ID;", "getTypingChats", "()Lkotlinx/coroutines/flow/StateFlow;", "closeEventStream", "", "getChat", "Lkotlin/Result;", "Lcom/getcode/oct24/data/RoomWithMembers;", "identifier", "Lcom/getcode/oct24/data/ChatIdentifier;", "getChat-gIAlu-s", "(Lcom/getcode/oct24/data/ChatIdentifier;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getChatMembers", "Lcom/getcode/oct24/data/Member;", "getChatMembers-gIAlu-s", "getChats", "Lcom/getcode/oct24/data/Room;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "getChats-gIAlu-s", "(Lcom/getcode/oct24/domain/model/query/QueryOptions;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "joinChat", "joinChat-gIAlu-s", "leaveChat", "chatId", "leaveChat-gIAlu-s", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "mute", "mute-gIAlu-s", "observeTyping", "Lkotlinx/coroutines/flow/Flow;", "", "conversationId", "openEventStream", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "removeUser", "userId", "removeUser-0E7RQCE", "(Ljava/util/List;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "startChat", "type", "Lcom/getcode/oct24/data/StartChatRequestType;", "startChat-gIAlu-s", "(Lcom/getcode/oct24/data/StartChatRequestType;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "unmute", "unmute-gIAlu-s", "flipchat_debug"})
public final class RealChatRepository implements com.getcode.oct24.internal.network.repository.chat.ChatRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.service.ChatService service = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.MetadataRoomMapper roomMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper roomWithMemberCountMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper roomWithMembersMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.MemberUpdateMapper memberUpdateMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.ConversationMemberMapper conversationMemberMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.LastMessageMapper messageMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy db$delegate = null;
    @org.jetbrains.annotations.Nullable()
    private com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsRequest, com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse> homeStreamReference;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<java.util.List<java.lang.Byte>>> _typingChats = null;
    
    @javax.inject.Inject()
    public RealChatRepository(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.ChatService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.MetadataRoomMapper roomMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper roomWithMemberCountMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper roomWithMembersMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.MemberUpdateMapper memberUpdateMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ConversationMemberMapper conversationMemberMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.LastMessageMapper messageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper) {
        super();
    }
    
    private final com.getcode.oct24.internal.db.FcAppDatabase getDb() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.StateFlow<java.util.List<java.util.List<java.lang.Byte>>> getTypingChats() {
        return null;
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public kotlinx.coroutines.flow.Flow<java.lang.Boolean> observeTyping(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> conversationId) {
        return null;
    }
    
    @java.lang.Override()
    public void openEventStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope coroutineScope) {
    }
    
    @java.lang.Override()
    public void closeEventStream() {
    }
}