package com.getcode.oct24.internal.network.api;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000T\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J.\u0010\b\u001a\b\u0012\u0004\u0012\u00020\n0\t2\u0010\u0010\u000b\u001a\f\u0012\u0004\u0012\u00020\r0\fj\u0002`\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0010J\u0014\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\t2\u0006\u0010\u000f\u001a\u00020\u0010J\u001c\u0010\u0014\u001a\b\u0012\u0004\u0012\u00020\u00150\t2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0016\u001a\u00020\u0017J.\u0010\u0018\u001a\b\u0012\u0004\u0012\u00020\u00190\t2\u0010\u0010\u000b\u001a\f\u0012\u0004\u0012\u00020\r0\fj\u0002`\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u001a\u001a\u00020\u0010R\u0016\u0010\u0005\u001a\n \u0007*\u0004\u0018\u00010\u00060\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001b"}, d2 = {"Lcom/getcode/oct24/internal/network/api/AccountApi;", "Lcom/getcode/oct24/internal/network/core/GrpcApi;", "managedChannel", "Lio/grpc/ManagedChannel;", "(Lio/grpc/ManagedChannel;)V", "api", "Lcom/codeinc/flipchat/gen/account/v1/AccountGrpc$AccountStub;", "kotlin.jvm.PlatformType", "authorizePublicKey", "Lkotlinx/coroutines/flow/Flow;", "Lcom/codeinc/flipchat/gen/account/v1/AccountService$AuthorizePublicKeyResponse;", "userId", "", "", "Lcom/getcode/model/ID;", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "newKeyPair", "login", "Lcom/codeinc/flipchat/gen/account/v1/AccountService$LoginResponse;", "register", "Lcom/codeinc/flipchat/gen/account/v1/AccountService$RegisterResponse;", "displayName", "", "revokePublicKey", "Lcom/codeinc/flipchat/gen/account/v1/AccountService$RevokePublicKeyResponse;", "keypair", "flipchat_debug"})
public final class AccountApi extends com.getcode.oct24.internal.network.core.GrpcApi {
    private final com.codeinc.flipchat.gen.account.v1.AccountGrpc.AccountStub api = null;
    
    @javax.inject.Inject()
    public AccountApi(@com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    io.grpc.ManagedChannel managedChannel) {
        super(null);
    }
    
    /**
     * Register registers a new user, bound to the provided PublicKey.
     * If the PublicKey is already in use, the previous user account is returned.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.account.v1.AccountService.RegisterResponse> register(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    java.lang.String displayName) {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.account.v1.AccountService.LoginResponse> login(@org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner) {
        return null;
    }
    
    /**
     * Authorizes an additional PublicKey to an account.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.account.v1.AccountService.AuthorizePublicKeyResponse> authorizePublicKey(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair newKeyPair) {
        return null;
    }
    
    /**
     * Revokes a public key from an account.
     *
     * There must be at least one public key per account. For now, any authorized public key
     * may revoke another public key, but this may change in the future.
     */
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.Flow<com.codeinc.flipchat.gen.account.v1.AccountService.RevokePublicKeyResponse> revokePublicKey(@org.jetbrains.annotations.NotNull()
    java.util.List<java.lang.Byte> userId, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair owner, @org.jetbrains.annotations.NotNull()
    com.getcode.ed25519.Ed25519.KeyPair keypair) {
        return null;
    }
}