package com.getcode.oct24.internal.network.repository.accounts;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0001\u0018\u00002\u00020\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J&\u0010\u0007\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000b0\bH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\f\u0010\rJ.\u0010\u000e\u001a\u0012\u0012\u000e\u0012\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000b0\b2\u0006\u0010\u000f\u001a\u00020\u0010H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0011\u0010\u0012R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0013"}, d2 = {"Lcom/getcode/oct24/internal/network/repository/accounts/RealAccountRepository;", "Lcom/getcode/oct24/internal/network/repository/accounts/AccountRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "service", "Lcom/getcode/oct24/internal/network/service/AccountService;", "(Lcom/getcode/oct24/user/UserManager;Lcom/getcode/oct24/internal/network/service/AccountService;)V", "login", "Lkotlin/Result;", "", "", "Lcom/getcode/model/ID;", "login-IoAF18A", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "register", "displayName", "", "register-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class RealAccountRepository implements com.getcode.oct24.internal.network.repository.accounts.AccountRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.service.AccountService service = null;
    
    @javax.inject.Inject()
    public RealAccountRepository(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.AccountService service) {
        super();
    }
}