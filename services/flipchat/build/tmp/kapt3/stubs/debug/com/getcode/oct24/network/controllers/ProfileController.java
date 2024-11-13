package com.getcode.oct24.network.controllers;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\u0018\u00002\u00020\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J.\u0010\u0005\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\u0010\u0010\b\u001a\f\u0012\u0004\u0012\u00020\n0\tj\u0002`\u000bH\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\f\u0010\rJ$\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u000f0\u00062\u0006\u0010\u0010\u001a\u00020\u0011H\u0086@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0012\u0010\u0013R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0014"}, d2 = {"Lcom/getcode/oct24/network/controllers/ProfileController;", "", "repository", "Lcom/getcode/oct24/internal/network/repository/profile/ProfileRepository;", "(Lcom/getcode/oct24/internal/network/repository/profile/ProfileRepository;)V", "getProfile", "Lkotlin/Result;", "Lcom/getcode/oct24/domain/model/profile/UserProfile;", "userId", "", "", "Lcom/getcode/model/ID;", "getProfile-gIAlu-s", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setDisplayName", "", "name", "", "setDisplayName-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class ProfileController {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.repository.profile.ProfileRepository repository = null;
    
    @javax.inject.Inject()
    public ProfileController(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.repository.profile.ProfileRepository repository) {
        super();
    }
}