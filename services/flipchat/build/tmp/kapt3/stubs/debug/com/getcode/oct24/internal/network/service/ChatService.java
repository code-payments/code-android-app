package com.getcode.oct24.internal.network.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0088\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\t\b\u0000\u0018\u00002\u00020\u0001:\u000656789:B\u0019\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J,\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u000e\u0010\u000fJ4\u0010\u0010\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00120\u00110\b2\u0006\u0010\n\u001a\u00020\u000b2\b\b\u0002\u0010\u0013\u001a\u00020\u0014H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0015\u0010\u0016J,\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0018\u0010\u000fJ6\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001a0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001c0\u0011j\u0002`\u001dH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u001e\u0010\u001fJB\u0010 \u001a\u00020\u001a2\u0006\u0010\n\u001a\u00020\u000b2\u0016\u0010!\u001a\u0012\u0012\u0004\u0012\u00020#\u0012\u0004\u0012\u00020$0\"j\u0002`%2\u0018\u0010&\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020(0\b\u0012\u0004\u0012\u00020\u001a0\'H\u0002J@\u0010 \u001a\u0012\u0012\u0004\u0012\u00020#\u0012\u0004\u0012\u00020$0\"j\u0002`%2\u0006\u0010)\u001a\u00020*2\u0006\u0010\n\u001a\u00020\u000b2\u0018\u0010&\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020(0\b\u0012\u0004\u0012\u00020\u001a0\'J>\u0010+\u001a\b\u0012\u0004\u0012\u00020\u001a0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\u001b\u001a\f\u0012\u0004\u0012\u00020\u001c0\u0011j\u0002`\u001d2\u0006\u0010,\u001a\u00020-H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b.\u0010/J,\u00100\u001a\b\u0012\u0004\u0012\u00020\u00120\b2\u0006\u0010\n\u001a\u00020\u000b2\u0006\u00101\u001a\u000202H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b3\u00104R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006;"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService;", "", "api", "Lcom/getcode/oct24/internal/network/api/ChatApi;", "networkOracle", "Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "(Lcom/getcode/oct24/internal/network/api/ChatApi;Lcom/getcode/oct24/internal/network/core/NetworkOracle;)V", "getChat", "Lkotlin/Result;", "Lcom/getcode/oct24/internal/network/model/chat/GetOrJoinChatResponse;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "identifier", "Lcom/getcode/oct24/data/ChatIdentifier;", "getChat-0E7RQCE", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Lcom/getcode/oct24/data/ChatIdentifier;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getChats", "", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$Metadata;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "getChats-0E7RQCE", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Lcom/getcode/oct24/domain/model/query/QueryOptions;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "joinChat", "joinChat-0E7RQCE", "leaveChat", "", "chatId", "", "Lcom/getcode/model/ID;", "leaveChat-0E7RQCE", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "openChatStream", "reference", "Lcom/getcode/services/observers/BidirectionalStreamReference;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsRequest;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsResponse;", "Lcom/getcode/oct24/internal/network/service/ChatHomeStreamReference;", "onEvent", "Lkotlin/Function1;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$StreamChatEventsResponse$ChatUpdate;", "scope", "Lkotlinx/coroutines/CoroutineScope;", "setMuteState", "muted", "", "setMuteState-BWLJW6A", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "startChat", "type", "Lcom/getcode/oct24/data/StartChatRequestType;", "startChat-0E7RQCE", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Lcom/getcode/oct24/data/StartChatRequestType;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "GetChatError", "GetChatsError", "JoinChatError", "LeaveChatError", "MuteStateError", "StartChatError", "flipchat_debug"})
public final class ChatService {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.api.ChatApi api = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.core.NetworkOracle networkOracle = null;
    
    @javax.inject.Inject()
    public ChatService(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.api.ChatApi api, @com.getcode.oct24.internal.annotations.FcNetworkOracle()
    @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.core.NetworkOracle networkOracle) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsRequest, com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse> openChatStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope scope, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super kotlin.Result<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse.ChatUpdate>, kotlin.Unit> onEvent) {
        return null;
    }
    
    private final void openChatStream(com.getcode.ed25519.Ed25519.KeyPair owner, com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsRequest, com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse> reference, kotlin.jvm.functions.Function1<? super kotlin.Result<com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse.ChatUpdate>, kotlin.Unit> onEvent) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0003\u0006\u0007\b\u00a8\u0006\t"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError;", "", "()V", "NotFound", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$NotFound;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$Unrecognized;", "flipchat_debug"})
    public static abstract class GetChatError extends java.lang.Throwable {
        
        private GetChatError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$NotFound;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class NotFound extends com.getcode.oct24.internal.network.service.ChatService.GetChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.GetChatError.NotFound INSTANCE = null;
            
            private NotFound() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.GetChatError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.GetChatError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.GetChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.GetChatError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0002\u0005\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError;", "", "()V", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError$Unrecognized;", "flipchat_debug"})
    public static abstract class GetChatsError extends java.lang.Throwable {
        
        private GetChatsError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.GetChatsError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.GetChatsError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$GetChatsError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.GetChatsError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.GetChatsError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0003\u0006\u0007\b\u00a8\u0006\t"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError;", "", "()V", "Denied", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Unrecognized;", "flipchat_debug"})
    public static abstract class JoinChatError extends java.lang.Throwable {
        
        private JoinChatError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.ChatService.JoinChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.JoinChatError.Denied INSTANCE = null;
            
            private Denied() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.JoinChatError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.JoinChatError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$JoinChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.JoinChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.JoinChatError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0016\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0002\u0003\u0004B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0002\u0005\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError;", "", "()V", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError$Unrecognized;", "flipchat_debug"})
    public static abstract class LeaveChatError extends java.lang.Throwable {
        
        private LeaveChatError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.LeaveChatError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.LeaveChatError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$LeaveChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.LeaveChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.LeaveChatError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0004\u0003\u0004\u0005\u0006B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0004\u0007\b\t\n\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError;", "", "()V", "CantMute", "Denied", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$CantMute;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Unrecognized;", "flipchat_debug"})
    public static abstract class MuteStateError extends java.lang.Throwable {
        
        private MuteStateError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$CantMute;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class CantMute extends com.getcode.oct24.internal.network.service.ChatService.MuteStateError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.MuteStateError.CantMute INSTANCE = null;
            
            private CantMute() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.ChatService.MuteStateError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.MuteStateError.Denied INSTANCE = null;
            
            private Denied() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.MuteStateError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.MuteStateError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$MuteStateError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.MuteStateError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.MuteStateError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0004\u0003\u0004\u0005\u0006B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0004\u0007\b\t\n\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError;", "", "()V", "Denied", "Other", "Unrecognized", "UserNotFound", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$UserNotFound;", "flipchat_debug"})
    public static abstract class StartChatError extends java.lang.Throwable {
        
        private StartChatError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Denied;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.ChatService.StartChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.StartChatError.Denied INSTANCE = null;
            
            private Denied() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Other;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.ChatService.StartChatError {
            @org.jetbrains.annotations.Nullable()
            private final java.lang.Throwable cause = null;
            
            public Other(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.Nullable()
            public java.lang.Throwable getCause() {
                return null;
            }
            
            public Other() {
            }
            
            @org.jetbrains.annotations.Nullable()
            public final java.lang.Throwable component1() {
                return null;
            }
            
            @org.jetbrains.annotations.NotNull()
            public final com.getcode.oct24.internal.network.service.ChatService.StartChatError.Other copy(@org.jetbrains.annotations.Nullable()
            java.lang.Throwable cause) {
                return null;
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.ChatService.StartChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.StartChatError.Unrecognized INSTANCE = null;
            
            private Unrecognized() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError$UserNotFound;", "Lcom/getcode/oct24/internal/network/service/ChatService$StartChatError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class UserNotFound extends com.getcode.oct24.internal.network.service.ChatService.StartChatError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.ChatService.StartChatError.UserNotFound INSTANCE = null;
            
            private UserNotFound() {
            }
            
            @java.lang.Override()
            public boolean equals(@org.jetbrains.annotations.Nullable()
            java.lang.Object other) {
                return false;
            }
            
            @java.lang.Override()
            public int hashCode() {
                return 0;
            }
            
            @java.lang.Override()
            @org.jetbrains.annotations.NotNull()
            public java.lang.String toString() {
                return null;
            }
        }
    }
}