package com.getcode.oct24.internal.network.repository.profile;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0010\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0000\u0018\u00002\u00020\u0001B\u001f\b\u0007\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ.\u0010\t\u001a\b\u0012\u0004\u0012\u00020\u000b0\n2\u0010\u0010\f\u001a\f\u0012\u0004\u0012\u00020\u000e0\rj\u0002`\u000fH\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0010\u0010\u0011J$\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00130\n2\u0006\u0010\u0014\u001a\u00020\u0015H\u0096@\u00f8\u0001\u0000\u00f8\u0001\u0001\u00a2\u0006\u0004\b\u0016\u0010\u0017R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u0082\u0002\u000b\n\u0002\b!\n\u0005\b\u00a1\u001e0\u0001\u00a8\u0006\u0018"}, d2 = {"Lcom/getcode/oct24/internal/network/repository/profile/RealProfileRepository;", "Lcom/getcode/oct24/internal/network/repository/profile/ProfileRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "service", "Lcom/getcode/oct24/internal/network/service/ProfileService;", "profileMapper", "Lcom/getcode/oct24/internal/data/mapper/ProfileMapper;", "(Lcom/getcode/oct24/user/UserManager;Lcom/getcode/oct24/internal/network/service/ProfileService;Lcom/getcode/oct24/internal/data/mapper/ProfileMapper;)V", "getProfile", "Lkotlin/Result;", "Lcom/getcode/oct24/domain/model/profile/UserProfile;", "userId", "", "", "Lcom/getcode/model/ID;", "getProfile-gIAlu-s", "(Ljava/util/List;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "setDisplayName", "", "name", "", "setDisplayName-gIAlu-s", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "flipchat_debug"})
public final class RealProfileRepository implements com.getcode.oct24.internal.network.repository.profile.ProfileRepository {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.network.service.ProfileService service = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.ProfileMapper profileMapper = null;
    
    @javax.inject.Inject()
    public RealProfileRepository(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.ProfileService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ProfileMapper profileMapper) {
        super();
    }
}