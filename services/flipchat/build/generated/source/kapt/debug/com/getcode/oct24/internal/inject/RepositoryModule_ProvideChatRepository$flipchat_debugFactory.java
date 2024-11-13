package com.getcode.oct24.internal.inject;

import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper;
import com.getcode.oct24.domain.mapper.RoomConversationMapper;
import com.getcode.oct24.internal.data.mapper.ConversationMemberMapper;
import com.getcode.oct24.internal.data.mapper.LastMessageMapper;
import com.getcode.oct24.internal.data.mapper.MemberUpdateMapper;
import com.getcode.oct24.internal.data.mapper.MetadataRoomMapper;
import com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper;
import com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper;
import com.getcode.oct24.internal.network.repository.chat.ChatRepository;
import com.getcode.oct24.internal.network.service.ChatService;
import com.getcode.oct24.user.UserManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class RepositoryModule_ProvideChatRepository$flipchat_debugFactory implements Factory<ChatRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<ChatService> serviceProvider;

  private final Provider<MetadataRoomMapper> roomMapperProvider;

  private final Provider<RoomConversationMapper> conversationMapperProvider;

  private final Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider;

  private final Provider<RoomWithMembersMapper> roomWithMembersMapperProvider;

  private final Provider<MemberUpdateMapper> memberUpdateMapperProvider;

  private final Provider<ConversationMemberMapper> conversationMemberMapperProvider;

  private final Provider<LastMessageMapper> messageMapperProvider;

  private final Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider;

  public RepositoryModule_ProvideChatRepository$flipchat_debugFactory(
      Provider<UserManager> userManagerProvider, Provider<ChatService> serviceProvider,
      Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider,
      Provider<RoomWithMembersMapper> roomWithMembersMapperProvider,
      Provider<MemberUpdateMapper> memberUpdateMapperProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<LastMessageMapper> messageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.roomMapperProvider = roomMapperProvider;
    this.conversationMapperProvider = conversationMapperProvider;
    this.roomWithMemberCountMapperProvider = roomWithMemberCountMapperProvider;
    this.roomWithMembersMapperProvider = roomWithMembersMapperProvider;
    this.memberUpdateMapperProvider = memberUpdateMapperProvider;
    this.conversationMemberMapperProvider = conversationMemberMapperProvider;
    this.messageMapperProvider = messageMapperProvider;
    this.messageWithContentMapperProvider = messageWithContentMapperProvider;
  }

  @Override
  public ChatRepository get() {
    return provideChatRepository$flipchat_debug(userManagerProvider.get(), serviceProvider.get(), roomMapperProvider.get(), conversationMapperProvider.get(), roomWithMemberCountMapperProvider.get(), roomWithMembersMapperProvider.get(), memberUpdateMapperProvider.get(), conversationMemberMapperProvider.get(), messageMapperProvider.get(), messageWithContentMapperProvider.get());
  }

  public static RepositoryModule_ProvideChatRepository$flipchat_debugFactory create(
      Provider<UserManager> userManagerProvider, Provider<ChatService> serviceProvider,
      Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider,
      Provider<RoomWithMembersMapper> roomWithMembersMapperProvider,
      Provider<MemberUpdateMapper> memberUpdateMapperProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<LastMessageMapper> messageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    return new RepositoryModule_ProvideChatRepository$flipchat_debugFactory(userManagerProvider, serviceProvider, roomMapperProvider, conversationMapperProvider, roomWithMemberCountMapperProvider, roomWithMembersMapperProvider, memberUpdateMapperProvider, conversationMemberMapperProvider, messageMapperProvider, messageWithContentMapperProvider);
  }

  public static ChatRepository provideChatRepository$flipchat_debug(UserManager userManager,
      ChatService service, MetadataRoomMapper roomMapper, RoomConversationMapper conversationMapper,
      RoomWithMemberCountMapper roomWithMemberCountMapper,
      RoomWithMembersMapper roomWithMembersMapper, MemberUpdateMapper memberUpdateMapper,
      ConversationMemberMapper conversationMemberMapper, LastMessageMapper messageMapper,
      ConversationMessageWithContentMapper messageWithContentMapper) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.provideChatRepository$flipchat_debug(userManager, service, roomMapper, conversationMapper, roomWithMemberCountMapper, roomWithMembersMapper, memberUpdateMapper, conversationMemberMapper, messageMapper, messageWithContentMapper));
  }
}
