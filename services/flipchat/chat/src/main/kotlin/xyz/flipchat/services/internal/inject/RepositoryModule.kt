package xyz.flipchat.services.internal.inject

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.flipchat.services.domain.mapper.ConversationMessageMapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.internal.data.mapper.ChatMessageMapper
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.data.mapper.LastMessageMapper
import xyz.flipchat.services.internal.data.mapper.MemberUpdateMapper
import xyz.flipchat.services.internal.data.mapper.MetadataRoomMapper
import xyz.flipchat.services.internal.data.mapper.MetadataUpdateMapper
import xyz.flipchat.services.internal.data.mapper.UserProfileMapper
import xyz.flipchat.services.internal.data.mapper.RoomWithMembersMapper
import xyz.flipchat.services.internal.data.mapper.SocialProfileMapper
import xyz.flipchat.services.internal.data.mapper.StreamMetadataUpdateMapper
import xyz.flipchat.services.internal.data.mapper.TypingMapper
import xyz.flipchat.services.internal.data.mapper.UserFlagsMapper
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import xyz.flipchat.services.internal.network.repository.accounts.RealAccountRepository
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.chat.RealChatRepository
import xyz.flipchat.services.internal.network.repository.iap.InAppPurchaseRepository
import xyz.flipchat.services.internal.network.repository.iap.RealInAppPurchaseRepository
import xyz.flipchat.services.internal.network.repository.messaging.MessagingRepository
import xyz.flipchat.services.internal.network.repository.messaging.RealMessagingRepository
import xyz.flipchat.services.internal.network.repository.profile.ProfileRepository
import xyz.flipchat.services.internal.network.repository.profile.RealProfileRepository
import xyz.flipchat.services.internal.network.repository.push.PushRepository
import xyz.flipchat.services.internal.network.repository.push.RealPushRepository
import xyz.flipchat.services.internal.network.service.AccountService
import xyz.flipchat.services.internal.network.service.ChatService
import xyz.flipchat.services.internal.network.service.MessagingService
import xyz.flipchat.services.internal.network.service.ProfileService
import xyz.flipchat.services.internal.network.service.PurchaseService
import xyz.flipchat.services.internal.network.service.PushService
import xyz.flipchat.services.user.UserManager

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryModule {

    @Provides
    internal fun providesAccountRepository(
        userManager: UserManager,
        service: AccountService,
        userFlagsMapper: UserFlagsMapper,
    ): AccountRepository = RealAccountRepository(userManager, service, userFlagsMapper)

    @Provides
    internal fun provideChatRepository(
        userManager: UserManager,
        service: ChatService,
        roomMapper: MetadataRoomMapper,
        conversationMapper: RoomConversationMapper,
        roomWithMembersMapper: RoomWithMembersMapper,
        memberUpdateMapper: MemberUpdateMapper,
        metadataUpdateMapper: MetadataUpdateMapper,
        streamMetadataUpdateMapper: StreamMetadataUpdateMapper,
        conversationMemberMapper: ConversationMemberMapper,
        messageMapper: LastMessageMapper,
        messageWithContentMapper: ConversationMessageMapper,
    ): ChatRepository = RealChatRepository(
        userManager = userManager,
        service = service,
        roomMapper = roomMapper,
        roomWithMembersMapper = roomWithMembersMapper,
        memberUpdateMapper = memberUpdateMapper,
        lastMessageMapper = messageMapper,
        messageMapper = messageWithContentMapper,
        metadataUpdateMapper = metadataUpdateMapper,
        streamMetadataUpdateMapper = streamMetadataUpdateMapper
    )

    @Provides
    internal fun providesInAppPurchaseRepository(
        userManager: UserManager,
        service: PurchaseService
    ): InAppPurchaseRepository = RealInAppPurchaseRepository(userManager, service)

    @Provides
    internal fun providesMessagingRepository(
        userManager: UserManager,
        service: MessagingService,
        messageMapper: ChatMessageMapper,
        lastMessageMapper: LastMessageMapper,
        messageWithContentMapper: ConversationMessageMapper,
        typingMapper: TypingMapper,
    ): MessagingRepository = RealMessagingRepository(
        userManager = userManager,
        service = service,
        chatMessageMapper = messageMapper,
        lastMessageMapper = lastMessageMapper,
        messageMapper = messageWithContentMapper,
        typingMapper = typingMapper,
    )

    @Provides
    internal fun providesProfileRepository(
        userManager: UserManager,
        service: ProfileService,
        userProfileMapper: UserProfileMapper,
        socialProfileMapper: SocialProfileMapper,
    ): ProfileRepository = RealProfileRepository(
        userManager = userManager,
        service = service,
        userProfileMapper = userProfileMapper,
        socialProfileMapper = socialProfileMapper,
    )

    @Provides
    internal fun providesPushRepository(
        service: PushService
    ): PushRepository = RealPushRepository(service = service)
}