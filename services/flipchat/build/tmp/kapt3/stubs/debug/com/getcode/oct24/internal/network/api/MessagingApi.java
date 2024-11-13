package com.getcode.oct24.internal.network.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000x\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J@\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0010\u0010\u0011\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010\u0012\u001a\u00020\u0013J.\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010\u0016\u001a\u00020\u0017J6\u0010\u0018\u001a\u00020\u00192\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010\u001a\u001a\u00020\u001b2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001e0\u001dJ6\u0010\u001f\u001a\u00020\u00192\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010 \u001a\u00020!2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\"0\u001dJ\u001c\u0010#\u001a\n\u0012\u0004\u0012\u00020$\u0018\u00010\u001d2\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020%0\u001dR\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006&"}, d2 = {"Lcom/getcode/oct24/internal/network/api/MessagingApi;", "Lcom/getcode/oct24/internal/network/core/GrpcApi;", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "api", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingGrpc$MessagingStub;", "kotlin.jvm.PlatformType", "advancePointer", "Lkotlinx/coroutines/flow/Flow;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$AdvancePointerResponse;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "chatId", "", "", "Lcom/getcode/model/ID;", "to", "status", "Lcom/getcode/model/chat/MessageStatus;", "getMessages", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$GetMessagesResponse;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "notifyIsTyping", "", "isTyping", "", "observer", "Lio/grpc/stub/StreamObserver;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$NotifyIsTypingResponse;", "sendMessage", "content", "Lcom/getcode/services/model/chat/OutgoingMessageContent;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$SendMessageResponse;", "streamMessages", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesRequest;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesResponse;", "flipchat_debug"})
public final class MessagingApi extends com.getcode.oct24.internal.network.core.GrpcApi {
    private final com.codeinc.flipchat.gen.messaging.v1.MessagingGrpc.MessagingStub api = null;
    
    @javax.inject.Inject()
    public MessagingApi(@com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super(null);
    }
    
    /**
     * gets the set of messages for a chat member using a paged API
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.messaging.v1.MessagingService.GetMessagesResponse> getMessages(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.query.QueryOptions queryOptions) {
        return null;
    }
    
    /**
     * advances a pointer in message history for a chat member.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.messaging.v1.MessagingService.AdvancePointerResponse> advancePointer(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> to, @org.jetbrains.annotations.NotNull()
    com.getcode.model.chat.MessageStatus status) {
        return null;
    }
    
    /**
     * sends a message to a chat.
     */
    public final void sendMessage(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    com.getcode.services.model.chat.OutgoingMessageContent content, @org.jetbrains.annotations.NotNull()
    io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.messaging.v1.MessagingService.SendMessageResponse> observer) {
    }
    
    public final void notifyIsTyping(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, boolean isTyping, @org.jetbrains.annotations.NotNull()
    io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.messaging.v1.MessagingService.NotifyIsTypingResponse> observer) {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesRequest> streamMessages(@org.jetbrains.annotations.NotNull()
    io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesResponse> observer) {
        return null;
    }
}