package com.getcode.oct24.internal.inject;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u008a\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c1\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J]\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u000e2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0018H\u0001\u00a2\u0006\u0002\b\u0019J\u001d\u0010\u001a\u001a\u00020\u001b2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\u001cH\u0001\u00a2\u0006\u0002\b\u001dJ5\u0010\u001e\u001a\u00020\u001f2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020 2\u0006\u0010\u0015\u001a\u00020!2\u0006\u0010\"\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u0018H\u0001\u00a2\u0006\u0002\b#J%\u0010$\u001a\u00020%2\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020&2\u0006\u0010\'\u001a\u00020(H\u0001\u00a2\u0006\u0002\b)J\u0015\u0010*\u001a\u00020+2\u0006\u0010\u0007\u001a\u00020,H\u0001\u00a2\u0006\u0002\b-\u00a8\u0006."}, d2 = {"Lcom/getcode/oct24/internal/inject/RepositoryModule;", "", "()V", "provideChatRepository", "Lcom/getcode/oct24/internal/network/repository/chat/ChatRepository;", "userManager", "Lcom/getcode/oct24/user/UserManager;", "service", "Lcom/getcode/oct24/internal/network/service/ChatService;", "roomMapper", "Lcom/getcode/oct24/internal/data/mapper/MetadataRoomMapper;", "conversationMapper", "Lcom/getcode/oct24/domain/mapper/RoomConversationMapper;", "roomWithMemberCountMapper", "Lcom/getcode/oct24/internal/data/mapper/RoomWithMemberCountMapper;", "roomWithMembersMapper", "Lcom/getcode/oct24/internal/data/mapper/RoomWithMembersMapper;", "memberUpdateMapper", "Lcom/getcode/oct24/internal/data/mapper/MemberUpdateMapper;", "conversationMemberMapper", "Lcom/getcode/oct24/internal/data/mapper/ConversationMemberMapper;", "messageMapper", "Lcom/getcode/oct24/internal/data/mapper/LastMessageMapper;", "messageWithContentMapper", "Lcom/getcode/oct24/domain/mapper/ConversationMessageWithContentMapper;", "provideChatRepository$flipchat_debug", "providesAccountRepository", "Lcom/getcode/oct24/internal/network/repository/accounts/AccountRepository;", "Lcom/getcode/oct24/internal/network/service/AccountService;", "providesAccountRepository$flipchat_debug", "providesMessagingRepository", "Lcom/getcode/oct24/internal/network/repository/messaging/MessagingRepository;", "Lcom/getcode/oct24/internal/network/service/MessagingService;", "Lcom/getcode/oct24/internal/data/mapper/ChatMessageMapper;", "lastMessageMapper", "providesMessagingRepository$flipchat_debug", "providesProfileRepository", "Lcom/getcode/oct24/internal/network/repository/profile/ProfileRepository;", "Lcom/getcode/oct24/internal/network/service/ProfileService;", "profileMapper", "Lcom/getcode/oct24/internal/data/mapper/ProfileMapper;", "providesProfileRepository$flipchat_debug", "providesPushRepository", "Lcom/getcode/oct24/internal/network/repository/push/PushRepository;", "Lcom/getcode/oct24/internal/network/service/PushService;", "providesPushRepository$flipchat_debug", "flipchat_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class RepositoryModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.getcode.oct24.internal.inject.RepositoryModule INSTANCE = null;
    
    private RepositoryModule() {
        super();
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.repository.accounts.AccountRepository providesAccountRepository$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.AccountService service) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.repository.chat.ChatRepository provideChatRepository$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.ChatService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.MetadataRoomMapper roomMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.RoomConversationMapper conversationMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper roomWithMemberCountMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper roomWithMembersMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.MemberUpdateMapper memberUpdateMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ConversationMemberMapper conversationMemberMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.LastMessageMapper messageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.repository.messaging.MessagingRepository providesMessagingRepository$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.MessagingService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ChatMessageMapper messageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.LastMessageMapper lastMessageMapper, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper messageWithContentMapper) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.repository.profile.ProfileRepository providesProfileRepository$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.user.UserManager userManager, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.ProfileService service, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.data.mapper.ProfileMapper profileMapper) {
        return null;
    }
    
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.repository.push.PushRepository providesPushRepository$flipchat_debug(@org.jetbrains.annotations.NotNull()
    com.getcode.oct24.internal.network.service.PushService service) {
        return null;
    }
}