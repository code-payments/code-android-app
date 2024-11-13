package xyz.flipchat.services.internal.inject

import com.getcode.services.annotations.EcdsaLookup
import com.getcode.services.model.EcdsaTupleQuery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.flipchat.services.domain.mapper.ConversationMessageWithContentMapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.internal.data.mapper.ChatMessageMapper
import xyz.flipchat.services.internal.data.mapper.ConversationMemberMapper
import xyz.flipchat.services.internal.data.mapper.LastMessageMapper
import xyz.flipchat.services.internal.data.mapper.MemberUpdateMapper
import xyz.flipchat.services.internal.data.mapper.MetadataRoomMapper
import xyz.flipchat.services.internal.data.mapper.ProfileMapper
import xyz.flipchat.services.internal.data.mapper.RoomWithMemberCountMapper
import xyz.flipchat.services.internal.data.mapper.RoomWithMembersMapper
import xyz.flipchat.services.internal.network.repository.accounts.AccountRepository
import xyz.flipchat.services.internal.network.repository.accounts.RealAccountRepository
import xyz.flipchat.services.internal.network.repository.chat.ChatRepository
import xyz.flipchat.services.internal.network.repository.chat.RealChatRepository
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
import xyz.flipchat.services.internal.network.service.PushService

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryModule {

    @Provides
    internal fun providesAccountRepository(
        @EcdsaLookup lookup: EcdsaTupleQuery,
        service: AccountService,
    ): AccountRepository = RealAccountRepository(lookup, service)

    @Provides
    internal fun provideChatRepository(
        @EcdsaLookup lookup: EcdsaTupleQuery,
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
        storedEcda = lookup,
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
        @EcdsaLookup lookup: EcdsaTupleQuery,
        service: MessagingService,
        messageMapper: ChatMessageMapper,
        lastMessageMapper: LastMessageMapper,
        messageWithContentMapper: ConversationMessageWithContentMapper
    ): MessagingRepository = RealMessagingRepository(
        storedEcda = lookup,
        service = service,
        messageMapper = messageMapper,
        lastMessageMapper = lastMessageMapper,
        messageWithContentMapper = messageWithContentMapper
    )

    @Provides
    internal fun providesProfileRepository(
        @EcdsaLookup lookup: EcdsaTupleQuery,
        service: ProfileService,
        profileMapper: ProfileMapper,
    ): ProfileRepository = RealProfileRepository(
        storedEcda = lookup,
        service = service,
        profileMapper = profileMapper
    )

    @Provides
    internal fun providesPushRepository(
        service: PushService
    ): PushRepository = RealPushRepository(service = service)
}