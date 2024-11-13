package com.getcode.oct24.internal.data.mapper;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u00002\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00030\u0001B\u000f\b\u0007\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0010\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\u0002H\u0016R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\t"}, d2 = {"Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;", "Lcom/getcode/services/mapper/Mapper;", "Lcom/codeinc/flipchat/gen/messaging/v1/Model$Message;", "Lcom/getcode/model/chat/ChatMessage;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "(Lcom/getcode/oct24/user/UserManager;)V", "map", "from", "flipchat_debug"})
public final class LastMessageMapper implements com.getcode.services.mapper.Mapper<com.codeinc.flipchat.gen.messaging.v1.Model.Message, com.getcode.model.chat.ChatMessage> {
    @org.jetbrains.annotations.NotNull()
    private final com.getcode.oct24.user.UserManager userManager = null;
    
    @javax.inject.Inject()
    public LastMessageMapper(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager) {
        super();
    }
    
    @java.lang.Override()
    @org.jetbrains.annotations.NotNull()
    public com.getcode.model.chat.ChatMessage map(@org.jetbrains.annotations.NotNull()
    com.codeinc.flipchat.gen.messaging.v1.Model.Message from) {
        return null;
    }
}