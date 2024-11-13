package com.getcode.oct24.internal.network.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J8\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0012J8\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\t2\u0006\u0010\u000b\u001a\u00020\f2\u0010\u0010\r\u001a\f\u0012\u0004\u0012\u00020\u000f0\u000ej\u0002`\u00102\u0006\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0012R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/getcode/oct24/internal/network/api/PushApi;", "Lcom/getcode/oct24/internal/network/core/GrpcApi;", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "api", "Lcom/codeinc/flipchat/gen/push/v1/PushGrpc$PushStub;", "kotlin.jvm.PlatformType", "addToken", "Lkotlinx/coroutines/flow/Flow;", "Lcom/codeinc/flipchat/gen/push/v1/PushService$AddTokenResponse;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "userId", "", "", "Lcom/getcode/model/ID;", "token", "", "installationId", "deleteToken", "Lcom/codeinc/flipchat/gen/push/v1/PushService$DeleteTokenResponse;", "flipchat_debug"})
public final class PushApi extends com.getcode.oct24.internal.network.core.GrpcApi {
    private final com.codeinc.flipchat.gen.push.v1.PushGrpc.PushStub api = null;
    
    @javax.inject.Inject()
    public PushApi(@com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.push.v1.PushService.AddTokenResponse> addToken(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.NotNull()
    java.lang.String token, @org.jetbrains.annotations.Nullable()
    java.lang.String installationId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.push.v1.PushService.DeleteTokenResponse> deleteToken(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.NotNull()
    java.lang.String token, @org.jetbrains.annotations.Nullable()
    java.lang.String installationId) {
        return null;
    }
}