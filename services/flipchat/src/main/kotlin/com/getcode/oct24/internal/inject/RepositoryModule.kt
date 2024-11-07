package com.getcode.oct24.internal.inject

import com.getcode.oct24.domain.mapper.RoomConversationMapper
import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper
import com.getcode.oct24.internal.data.mapper.ChatMessageMapper
import com.getcode.oct24.internal.data.mapper.ConversationMemberMapper
import com.getcode.oct24.internal.data.mapper.LastMessageMapper
import com.getcode.oct24.internal.data.mapper.MemberUpdateMapper
import com.getcode.oct24.internal.data.mapper.ProfileMapper
import com.getcode.oct24.internal.data.mapper.MetadataRoomMapper
import com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper
import com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper
import com.getcode.oct24.internal.network.repository.accounts.AccountRepository
import com.getcode.oct24.internal.network.repository.accounts.RealAccountRepository
import com.getcode.oct24.internal.network.repository.chat.ChatRepository
import com.getcode.oct24.internal.network.repository.chat.RealChatRepository
import com.getcode.oct24.internal.network.repository.messaging.MessagingRepository
import com.getcode.oct24.internal.network.repository.messaging.RealMessagingRepository
import com.getcode.oct24.internal.network.repository.profile.ProfileRepository
import com.getcode.oct24.internal.network.repository.profile.RealProfileRepository
import com.getcode.oct24.internal.network.repository.push.PushRepository
import com.getcode.oct24.internal.network.repository.push.RealPushRepository
import com.getcode.oct24.internal.network.service.AccountService
import com.getcode.oct24.internal.network.service.ChatService
import com.getcode.oct24.internal.network.service.MessagingService
import com.getcode.oct24.internal.network.service.ProfileService
import com.getcode.oct24.internal.network.service.PushService
import com.getcode.oct24.user.UserManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryModule {

    @Provides
    internal fun providesAccountRepository(
        userManager: UserManager,
        service: AccountService,
    ): AccountRepository = RealAccountRepository(userManager, service)

    @Provides
    internal fun provideChatRepository(
        userManager: UserManager,
        service: ChatService,
        roomMapper: MetadataRoomMapper,
        conversationMapper: RoomConversationMapper,
        roomWithMemberCountMapper: RoomWithMemberCountMapper,
        roomWithMembersMapper: RoomWithMembersMapper,
        memberUpdateMapper: MemberUpdateMapper,
        conversationMemberMapper: ConversationMemberMapper,
        messageMapper: LastMessageMapper,
        messageWithContentMapper: ConversationMessageWithContentMapper,
    ): ChatRepository = RealChatRepository(
        userManager = userManager,
        service = service,
        roomMapper = roomMapper,
        roomWithMemberCountMapper = roomWithMemberCountMapper,
        roomWithMembersMapper = roomWithMembersMapper,
        conversationMapper = conversationMapper,
        memberUpdateMapper = memberUpdateMapper,
        conversationMemberMapper = conversationMemberMapper,
        messageMapper = messageMapper,
        messageWithContentMapper = messageWithContentMapper
    )

    @Provides
    internal fun providesMessagingRepository(
        userManager: UserManager,
        service: MessagingService,
        messageMapper: ChatMessageMapper,
        lastMessageMapper: LastMessageMapper,
        messageWithContentMapper: ConversationMessageWithContentMapper
    ): MessagingRepository = RealMessagingRepository(
        userManager = userManager,
        service = service,
        messageMapper = messageMapper,
        lastMessageMapper = lastMessageMapper,
        messageWithContentMapper = messageWithContentMapper
    )

    @Provides
    internal fun providesProfileRepository(
        userManager: UserManager,
        service: ProfileService,
        profileMapper: ProfileMapper,
    ): ProfileRepository = RealProfileRepository(
        userManager = userManager,
        service = service,
        profileMapper = profileMapper
    )

    @Provides
    internal fun providesPushRepository(
        service: PushService
    ): PushRepository = RealPushRepository(service = service)
}