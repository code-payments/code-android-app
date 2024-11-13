package com.getcode.oct24.internal.network.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000~\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001c\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eJ\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00100\t2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0011\u001a\u00020\u0012J\u001c\u0010\u0013\u001a\b\u0012\u0004\u0012\u00020\u00140\t2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000eJ&\u0010\u0015\u001a\b\u0012\u0004\u0012\u00020\u00160\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\u0017\u001a\f\u0012\u0004\u0012\u00020\u00190\u0018j\u0002`\u001aJ.\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u001c0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\u0017\u001a\f\u0012\u0004\u0012\u00020\u00190\u0018j\u0002`\u001a2\u0006\u0010\u001d\u001a\u00020\u001eJ\u001c\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020 0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010!\u001a\u00020\"J\u001c\u0010#\u001a\n\u0012\u0004\u0012\u00020%\u0018\u00010$2\f\u0010&\u001a\b\u0012\u0004\u0012\u00020\'0$R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006("}, d2 = {"Lcom/getcode/oct24/internal/network/api/ChatApi;", "Lcom/getcode/oct24/internal/network/core/GrpcApi;", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "api", "Lcom/codeinc/flipchat/gen/chat/v1/ChatGrpc$ChatStub;", "kotlin.jvm.PlatformType", "getChat", "Lkotlinx/coroutines/flow/Flow;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$GetChatResponse;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "identifier", "Lcom/getcode/oct24/data/ChatIdentifier;", "getChats", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$GetChatsResponse;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "joinChat", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$JoinChatResponse;", "leaveChat", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$LeaveChatResponse;", "chatId", "", "", "Lcom/getcode/model/ID;", "setMuteState", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$SetMuteStateResponse;", "muted", "", "startChat", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StartChatResponse;", "type", "Lcom/getcode/oct24/data/StartChatRequestType;", "streamEvents", "Lio/grpc/stub/StreamObserver;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsRequest;", "observer", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsResponse;", "flipchat_debug"})
public final class ChatApi extends com.getcode.oct24.internal.network.core.GrpcApi {
    private final com.codeinc.flipchat.gen.chat.v1.ChatGrpc.ChatStub api = null;
    
    @javax.inject.Inject()
    public ChatApi(@com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StartChatResponse> startChat(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.data.StartChatRequestType type) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.GetChatsResponse> getChats(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.model.query.QueryOptions queryOptions) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.GetChatResponse> getChat(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.data.ChatIdentifier identifier) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.JoinChatResponse> joinChat(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.data.ChatIdentifier identifier) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.LeaveChatResponse> leaveChat(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.chat.v1.FlipchatService.SetMuteStateResponse> setMuteState(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, boolean muted) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsRequest> streamEvents(@org.jetbrains.annotations.NotNull()
    io.grpc.stub.StreamObserver<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse> observer) {
        return null;
    }
}