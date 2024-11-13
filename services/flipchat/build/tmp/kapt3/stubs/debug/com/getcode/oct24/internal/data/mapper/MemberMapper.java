package com.getcode.oct24.internal.data.mapper;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u00002\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0001B\u0017\b\u0007\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u00a2\u0006\u0002\u0010\bJ\u0010\u0010\t\u001a\u00020\u00032\u0006\u0010\n\u001a\u00020\u0002H\u0016R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/getcode/oct24/internal/data/mapper/MemberMapper;", "Lcom/getcode/services/mapper/Mapper;", "Lcom/codeinc/flipchat/gen/chat/v1/FlipchatService$Member;", "Lcom/getcode/oct24/data/Member;", "identityMapper", "Lcom/getcode/oct24/internal/data/mapper/MemberIdentityMapper;", "pointerModelMapper", "Lcom/getcode/oct24/internal/data/mapper/PointerModelMapper;", "(Lcom/getcode/oct24/internal/data/mapper/MemberIdentityMapper;Lcom/getcode/oct24/internal/data/mapper/PointerModelMapper;)V", "map", "from", "flipchat_debug"})
public final class MemberMapper implements com.getcode.services.mapper.Mapper<com.codeinc.flipchat.gen.chat.v1.FlipchatService.Member, com.getcode.oct24.data.Member> {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.MemberIdentityMapper identityMapper = null;
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.internal.data.mapper.PointerModelMapper pointerModelMapper = null;
    
    @javax.inject.Inject()
    public MemberMapper(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.MemberIdentityMapper identityMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.PointerModelMapper pointerModelMapper) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.getcode.oct24.data.Member map(@org.jetbrains.annotations.NotNull()
    com.codeinc.flipchat.gen.chat.v1.FlipchatService.Member from) {
        return null;
    }
}