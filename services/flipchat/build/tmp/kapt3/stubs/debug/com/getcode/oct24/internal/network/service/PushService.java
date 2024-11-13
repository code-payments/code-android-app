package com.getcode.oct24.internal.network.service;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0005\b\u0000\u0018\u00002\u00020\u0001:\u0001\u0015B\u0019\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0001\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006JH\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u0006\u0010\n\u001a\u00020\u000b2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000f2\u0006\u0010\u0010\u001a\u00020\u00112\b\u0010\u0012\u001a\u0004\u0018\u00010\u0011H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0013\u0010\u0014R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0016"}, d2 = {"Lcom/getcode/oct24/internal/network/service/PushService;", "", "api", "Lcom/getcode/oct24/internal/network/api/PushApi;", "networkOracle", "Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "(Lcom/getcode/oct24/internal/network/api/PushApi;Lcom/getcode/oct24/internal/network/core/NetworkOracle;)V", "addToken", "Lkotlin/Result;", "", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "userId", "", "", "Lcom/getcode/model/ID;", "token", "", "installationId", "addToken-yxL6bBk", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "AddTokenError", "flipchat_debug"})
public final class PushService {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.api.PushApi api = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.core.NetworkOracle networkOracle = null;
    
    @javax.inject.Inject()
    public PushService(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.api.PushApi api, @com.getcode.oct24.internal.annotations.FcNetworkOracle()
    @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.core.NetworkOracle networkOracle) {
        super();
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0003\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b0\u0018\u00002\u00020\u0001:\u0003\n\u000b\fB\u001f\b\u0004\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u0012\n\b\u0002\u0010\u0004\u001a\u0004\u0018\u00010\u0001\u00a2\u0006\u0002\u0010\u0005R\u0016\u0010\u0004\u001a\u0004\u0018\u00010\u0001X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u0082\u0001\u0003\r\u000e\u000f\u00a8\u0006\u0010"}, d2 = {"Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError;", "", "message", "", "cause", "(Ljava/lang/String;Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "getMessage", "()Ljava/lang/String;", "InvalidPushToken", "Other", "Unrecognized", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$InvalidPushToken;", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$Other;", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$Unrecognized;", "flipchat_debug"})
    public static abstract class AddTokenError extends java.lang.Throwable {
        @org.jetbrains.annotations.Nullable()
        private final java.lang.String message = null;
        @org.jetbrains.annotations.Nullable()
        private final java.lang.Throwable cause = null;
        
        private AddTokenError(java.lang.String message, java.lang.Throwable cause) {
            super(null);
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.Nullable()
        public java.lang.String getMessage() {
            return null;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.Nullable()
        public java.lang.Throwable getCause() {
            return null;
        }
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$InvalidPushToken;", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class InvalidPushToken extends com.getcode.oct24.internal.network.service.PushService.AddTokenError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.PushService.AddTokenError.InvalidPushToken INSTANCE = null;
            
            private InvalidPushToken() {
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0003\n\u0002\b\u0006\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003\u00a2\u0006\u0002\u0010\u0004J\u000b\u0010\u0007\u001a\u0004\u0018\u00010\u0003H\u00c6\u0003J\u0015\u0010\b\u001a\u00020\u00002\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u0003H\u00c6\u0001J\u0013\u0010\t\u001a\u00020\n2\b\u0010\u000b\u001a\u0004\u0018\u00010\fH\u00d6\u0003J\t\u0010\r\u001a\u00020\u000eH\u00d6\u0001J\t\u0010\u000f\u001a\u00020\u0010H\u00d6\u0001R\u0016\u0010\u0002\u001a\u0004\u0018\u00010\u0003X\u0096\u0004\u00a2\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006\u00a8\u0006\u0011"}, d2 = {"Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$Other;", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError;", "cause", "", "(Ljava/lang/Throwable;)V", "getCause", "()Ljava/lang/Throwable;", "component1", "copy", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Other extends com.getcode.oct24.internal.network.service.PushService.AddTokenError {
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
            public final com.getcode.oct24.internal.network.service.PushService.AddTokenError.Other copy(@org.jetbrains.annotations.Nullable()
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
        
        @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u00c6\n\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u0003\u001a\u00020\u00042\b\u0010\u0005\u001a\u0004\u0018\u00010\u0006H\u00d6\u0003J\t\u0010\u0007\u001a\u00020\bH\u00d6\u0001J\t\u0010\t\u001a\u00020\nH\u00d6\u0001\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError$Unrecognized;", "Lcom/getcode/oct24/internal/network/service/PushService$AddTokenError;", "()V", "equals", "", "other", "", "hashCode", "", "toString", "", "flipchat_debug"})
        public static final class Unrecognized extends com.getcode.oct24.internal.network.service.PushService.AddTokenError {
            @org.jetbrains.annotations.NotNull()
            public static final com.getcode.oct24.internal.network.service.PushService.AddTokenError.Unrecognized INSTANCE = null;
            
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