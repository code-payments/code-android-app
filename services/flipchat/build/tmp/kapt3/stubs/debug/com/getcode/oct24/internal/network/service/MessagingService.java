package com.getcode.oct24.internal.network.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0084\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0000\u0018\u00002\u00020\u0001:\u00043456B\u0019\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006JP\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\u0010\u0010\u0010\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\u0006\u0010\u0011\u001a\u00020\u0012H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0013\u0010\u0014JF\u0010\u0015\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\r0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\b\b\u0002\u0010\u0017\u001a\u00020\u0018H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0019\u0010\u001aJ>\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\u0006\u0010\u001c\u001a\u00020\u001dH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u001e\u0010\u001fJ\u0085\u0001\u0010 \u001a\u00020\t2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2*\u0010!\u001a&\b\u0001\u0012\u0018\u0012\u0016\u0012\u0012\u0012\u0010\u0012\u0004\u0012\u00020\u000e\u0018\u00010\rj\u0004\u0018\u0001`\u000f0#\u0012\u0006\u0012\u0004\u0018\u00010\u00010\"2\u0016\u0010$\u001a\u0012\u0012\u0004\u0012\u00020&\u0012\u0004\u0012\u00020\'0%j\u0002`(2\u0018\u0010)\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\b\u0012\u0004\u0012\u00020\t0\"H\u0002\u00a2\u0006\u0002\u0010*J\u0083\u0001\u0010 \u001a\u0012\u0012\u0004\u0012\u00020&\u0012\u0004\u0012\u00020\'0%j\u0002`(2\u0006\u0010+\u001a\u00020,2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2*\u0010!\u001a&\b\u0001\u0012\u0018\u0012\u0016\u0012\u0012\u0012\u0010\u0012\u0004\u0012\u00020\u000e\u0018\u00010\rj\u0004\u0018\u0001`\u000f0#\u0012\u0006\u0012\u0004\u0018\u00010\u00010\"2\u0018\u0010)\u001a\u0014\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00160\b\u0012\u0004\u0012\u00020\t0\"\u00a2\u0006\u0002\u0010-J>\u0010.\u001a\b\u0012\u0004\u0012\u00020\u00160\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\u0006\u0010/\u001a\u000200H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b1\u00102R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u00067"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService;", "", "api", "Lcom/getcode/oct24/internal/network/api/MessagingApi;", "networkOracle", "Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "(Lcom/getcode/oct24/internal/network/api/MessagingApi;Lcom/getcode/oct24/internal/network/core/NetworkOracle;)V", "advancePointer", "Lkotlin/Result;", "", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "chatId", "", "", "Lcom/getcode/model/ID;", "to", "status", "Lcom/getcode/model/chat/MessageStatus;", "advancePointer-yxL6bBk", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Ljava/util/List;Lcom/getcode/model/chat/MessageStatus;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getMessages", "Lcom/codeinc/flipchat/gen/messaging/v1/Model$Message;", "queryOptions", "Lcom/getcode/oct24/domain/model/query/QueryOptions;", "getMessages-BWLJW6A", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Lcom/getcode/oct24/domain/model/query/QueryOptions;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "notifyIsTyping", "isTyping", "", "notifyIsTyping-BWLJW6A", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;", "openMessageStream", "lastMessageId", "Lkotlin/Function1;", "Lkotlin/coroutines/Continuation;", "reference", "Lcom/getcode/services/observers/BidirectionalStreamReference;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesRequest;", "Lcom/codeinc/flipchat/gen/messaging/v1/MessagingService$StreamMessagesResponse;", "Lcom/getcode/oct24/internal/network/service/ChatMessageStreamReference;", "onEvent", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Lkotlin/jvm/functions/Function1;Lcom/getcode/services/observers/BidirectionalStreamReference;Lkotlin/jvm/functions/Function1;)V", "scope", "Lkotlinx/coroutines/CoroutineScope;", "(Lkotlinx/coroutines/CoroutineScope;Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)Lcom/getcode/services/observers/BidirectionalStreamReference;", "sendMessage", "content", "Lcom/getcode/services/model/chat/OutgoingMessageContent;", "sendMessage-BWLJW6A", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Lcom/getcode/services/model/chat/OutgoingMessageContent;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "AdvancePointerError", "GetMessagesError", "SendMessageError", "TypingChangeError", "flipchat_debug"})
public final class MessagingService {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.api.MessagingApi api = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.core.NetworkOracle networkOracle = null;
    
    @javax.inject.Inject()
    public MessagingService(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.api.MessagingApi api, @com.getcode.oct24.internal.annotations.FcNetworkOracle()
    @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.core.NetworkOracle networkOracle) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesRequest, com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesResponse> openMessageStream(@org.jetbrains.annotations.NotNull()
    kotlinx.coroutines.CoroutineScope scope, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> chatId, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super kotlin.coroutines.Continuation<? super java.util.List<java.lang.Byte>>, ? extends java.lang.Object> lastMessageId, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function1<? super kotlin.Result<com.codeinc.flipchat.gen.messaging.v1.Model.Message>, kotlin.Unit> onEvent) {
        return null;
    }
    
    private final void openMessageStream(com.getcode.ed25519.Ed25519.KeyPair owner, java.util.List<java.lang.Byte> chatId, kotlin.jvm.functions.Function1<? super kotlin.coroutines.Continuation<? super java.util.List<java.lang.Byte>>, ? extends java.lang.Object> lastMessageId, com.getcode.services.observers.BidirectionalStreamReference<com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesRequest, com.codeinc.flipchat.gen.messaging.v1.MessagingService.StreamMessagesResponse> reference, kotlin.jvm.functions.Function1<? super kotlin.Result<com.codeinc.flipchat.gen.messaging.v1.Model.Message>, kotlin.Unit> onEvent) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0006\u0006\u0007\b\t\n\u000b\u00a8\u0006\f"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "", "()V", "Denied", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Unrecognized;", "flipchat_debug"})
    public static abstract class AdvancePointerError extends java.lang.Throwable {
        
        private AdvancePointerError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError.Denied INSTANCE = null;
            
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
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
            public final com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError.Other copy(@org.jetbrains.annotations.Nullable()
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError.Unrecognized INSTANCE = null;
            
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u0082\u0001\u0007\u0006\u0007\b\t\n\u000b\f\u00a8\u0006\r"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "", "()V", "Denied", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$InvalidContentType;", "Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Unrecognized;", "flipchat_debug"})
    public static abstract class GetMessagesError extends java.lang.Throwable {
        
        private GetMessagesError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError.Denied INSTANCE = null;
            
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
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
            public final com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError.Other copy(@org.jetbrains.annotations.Nullable()
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError.Unrecognized INSTANCE = null;
            
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0006\b6\u0018\u00002\u00020\u0001:\u0004\u0003\u0004\u0005\u0006B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0007"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError;", "", "()V", "Denied", "InvalidContentType", "Other", "Unrecognized", "flipchat_debug"})
    public static abstract class SendMessageError extends java.lang.Throwable {
        
        private SendMessageError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.SendMessageError.Denied INSTANCE = null;
            
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$InvalidContentType;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class InvalidContentType extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.SendMessageError.InvalidContentType INSTANCE = null;
            
            private InvalidContentType() {
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
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
            public final com.getcode.oct24.internal.network.service.MessagingService.SendMessageError.Other copy(@org.jetbrains.annotations.Nullable()
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$SendMessageError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$GetMessagesError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.MessagingService.GetMessagesError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.SendMessageError.Unrecognized INSTANCE = null;
            
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
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0002\b\u0005\b6\u0018\u00002\u00020\u0001:\u0003\u0003\u0004\u0005B\u0007\b\u0004\u00a2\u0006\u0002\u0010\u0002\u00a8\u0006\u0006"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError;", "", "()V", "Denied", "Other", "Unrecognized", "flipchat_debug"})
    public static abstract class TypingChangeError extends java.lang.Throwable {
        
        private TypingChangeError() {
            super(null);
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Denied;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Denied extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.TypingChangeError.Denied INSTANCE = null;
            
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Other;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
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
            public final com.getcode.oct24.internal.network.service.MessagingService.TypingChangeError.Other copy(@org.jetbrains.annotations.Nullable()
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/MessagingService$TypingChangeError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/MessagingService$AdvancePointerError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.MessagingService.AdvancePointerError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.MessagingService.TypingChangeError.Unrecognized INSTANCE = null;
            
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
}