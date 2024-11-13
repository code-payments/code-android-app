package com.getcode.oct24.internal.network.repository.messaging;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0084\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B/\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\t\u0012\u0006\u0010\n\u001a\u00020\u000b\u00a2\u0006\u0002\u0010\fJH\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e2\u0010\u0010\u001f\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e2\u0006\u0010 \u001a\u00020!H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\"\u0010#J\b\u0010$\u001a\u00020\u001aH\u0016J@\u0010%\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e2\u0010\u0010\u001f\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b&\u0010\'J<\u0010(\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020)0\u001c0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e2\u0006\u0010*\u001a\u00020+H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b,\u0010-J.\u0010.\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b/\u00100J.\u00101\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b2\u00100J\"\u00103\u001a\u00020\u001a2\u0006\u00104\u001a\u0002052\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001eH\u0016J6\u00106\u001a\b\u0012\u0004\u0012\u00020)0\u00192\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001d0\u001cj\u0002`\u001e2\u0006\u00107\u001a\u000208H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b9\u0010:R\u001b\u0010\r\u001a\u00020\u000e8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0011\u0010\u0012\u001a\u0004\b\u000f\u0010\u0010R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\"\u0010\u0013\u001a\u0016\u0012\u0004\u0012\u00020\u0015\u0012\u0004\u0012\u00020\u0016\u0018\u00010\u0014j\u0004\u0018\u0001`\u0017X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006;"}, d2 = {"Lcom/getcode/oct24/internal/network/repository/messaging/RealMessagingRepository;", "Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "service", "Lcom/getcode/oct24/internal/network/service/MessagingService;", "messageMapper", "Lcom/getcode/oct24/internal/data/mapper/ChatMessageMapper;", "lastMessageMapper", "Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;", "messageWithContentMapper", "Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;", "(Lcom/getcode/oct24/user/UserManager;Lcom/getcode/oct24/internal/network/service/MessagingService;Lcom/getcode/oct24/internal/data/mapper/ChatMessageMapper;Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;)V", "db", "Lcom/getcode/oct24/internal/db/FcAppDatabase;", "getDb", "()Lcom/getcode/oct24/internal/db/FcAppDatabase;", "db$delegate", "Lkotlin/Lazy;", "messageStream", "Lcom/getcode/services/observers/BidirectionalStreamReference;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesRequest;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesResponse;", "Lcom/getcode/oct24/internal/network/service/ChatMessageStreamReference;", "advancePointer", "Lkotlin/Result;", "", "chatId", "", "", "Lcom/getcode/model/ID;", "messageId", "status", "Lcom/getcode/model/chat/MessageStatus;", "advancePointer-BWLJW6A", "(Ljava/util/List;Ljava/util/List;Lcom/getcode/model/chat/MessageStatus;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "closeMessageStream", "deleteMessage", "deleteMessage-0E7RQCE", "(Ljava/util/List;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getMessages", "Lcom/getcode/model/chat/ChatMessage;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "getMessages-0E7RQCE", "(Ljava/util/List;Lcom/getcode/oct24/domain/model/query/QueryOptions;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "onStartedTyping", "onStartedTyping-gIAlu-s", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "onStoppedTyping", "onStoppedTyping-gIAlu-s", "openMessageStream", "coroutineScope", "Lkotlinx/coroutines/CoroutineScope;", "sendMessage", "content", "Lcom/getcode/services/model/chat/OutgoingMessageContent;", "sendMessage-0E7RQCE", "(Ljava/util/List;Lcom/getcode/services/model/chat/OutgoingMessageContent;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class RealMessagingRepository implements com.getcode.oct24.internal.network.repository.messaging.MessagingRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.service.MessagingService service = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.ChatMessageMapper messageMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.LastMessageMapper lastMessageMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy db$delegate = null;
    @org.jetbrains.annotations.Nullable()
    private com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesRequest, com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesResponse> messageStream;
    
    @javax.inject.Inject()
    public RealMessagingRepository(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.MessagingService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ChatMessageMapper messageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.LastMessageMapper lastMessageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper) {
        super();
    }
    
    private final com.getcode.oct24.internal.db.FcAppDatabase getDb() {
        return null;
    }
    
    @java.lang.Override()
    public void openMessageStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope coroutineScope, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId) {
    }
    
    @java.lang.Override()
    public void closeMessageStream() {
    }
}