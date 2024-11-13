package com.getcode.oct24.internal.network.repository.push;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\b\u0000\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004JH\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0006\u0010\b\u001a\u00020\t2\u0010\u0010\n\u001a\f\u0012\u0004\u0012\u00020\f0\u000bj\u0002`\r2\u0006\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u000fH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0011\u0010\u0012R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0013"}, d2 = {"Lcom/getcode/oct24/internal/network/repository/push/RealPushRepository;", "Lcom/getcode/oct24/internal/network/repository/push/PushRepository;", "service", "Lcom/getcode/oct24/internal/network/service/PushService;", "(Lcom/getcode/oct24/internal/network/service/PushService;)V", "addToken", "Lkotlin/Result;", "", "owner", "Lcom/getcode/ed25519/Ed25519$KeyPair;", "userId", "", "", "Lcom/getcode/model/ID;", "token", "", "installationId", "addToken-yxL6bBk", "(Lcom/getcode/ed25519/Ed25519$KeyPair;Ljava/util/List;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class RealPushRepository implements com.getcode.oct24.internal.network.repository.push.PushRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.service.PushService service = null;
    
    @javax.inject.Inject()
    public RealPushRepository(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.PushService service) {
        super();
    }
}