package com.getcode.oct24.internal.network.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001e\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0010\u0010\u000b\u001a\f\u0012\u0004\u0012\u00020\r0\fj\u0002`\u000eJ\u001c\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00100\t2\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u0014R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/getcode/oct24/internal/network/api/ProfileApi;", "Lcom/getcode/oct24/internal/network/core/GrpcApi;", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "api", "Lcom/codeinc/flipchat/gen/profile/v1/ProfileGrpc$ProfileStub;", "kotlin.jvm.PlatformType", "getProfile", "Lkotlinx/coroutines/flow/Flow;", "Lcom/codeinc/flipchat/gen/profile/v1/ProfileService$GetProfileResponse;", "userId", "", "", "Lcom/getcode/model/ID;", "setDisplayName", "Lcom/codeinc/flipchat/gen/profile/v1/ProfileService$SetDisplayNameResponse;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "displayName", "", "flipchat_debug"})
public final class ProfileApi extends com.getcode.oct24.internal.network.core.GrpcApi {
    private final com.codeinc.flipchat.gen.profile.v1.ProfileGrpc.ProfileStub api = null;
    
    @javax.inject.Inject()
    public ProfileApi(@com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.profile.v1.ProfileService.GetProfileResponse> getProfile(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.profile.v1.ProfileService.SetDisplayNameResponse> setDisplayName(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.lang.String displayName) {
        return null;
    }
}