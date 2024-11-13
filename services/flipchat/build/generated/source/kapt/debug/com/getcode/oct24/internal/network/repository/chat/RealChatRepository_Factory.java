package com.getcode.oct24.internal.network.repository.chat;

import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper;
import com.getcode.oct24.domain.mapper.RoomConversationMapper;
import com.getcode.oct24.internal.data.mapper.ConversationMemberMapper;
import com.getcode.oct24.internal.data.mapper.LastMessageMapper;
import com.getcode.oct24.internal.data.mapper.MemberUpdateMapper;
import com.getcode.oct24.internal.data.mapper.MetadataRoomMapper;
import com.getcode.oct24.internal.data.mapper.RoomWithMemberCountMapper;
import com.getcode.oct24.internal.data.mapper.RoomWithMembersMapper;
import com.getcode.oct24.internal.network.service.ChatService;
import com.getcode.oct24.user.UserManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class RealChatRepository_Factory implements Factory<RealChatRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<ChatService> serviceProvider;

  private final Provider<MetadataRoomMapper> roomMapperProvider;

  private final Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider;

  private final Provider<RoomWithMembersMapper> roomWithMembersMapperProvider;

  private final Provider<RoomConversationMapper> conversationMapperProvider;

  private final Provider<MemberUpdateMapper> memberUpdateMapperProvider;

  private final Provider<ConversationMemberMapper> conversationMemberMapperProvider;

  private final Provider<LastMessageMapper> messageMapperProvider;

  private final Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider;

  public RealChatRepository_Factory(Provider<UserManager> userManagerProvider,
      Provider<ChatService> serviceProvider, Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider,
      Provider<RoomWithMembersMapper> roomWithMembersMapperProvider,
      Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<MemberUpdateMapper> memberUpdateMapperProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<LastMessageMapper> messageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.roomMapperProvider = roomMapperProvider;
    this.roomWithMemberCountMapperProvider = roomWithMemberCountMapperProvider;
    this.roomWithMembersMapperProvider = roomWithMembersMapperProvider;
    this.conversationMapperProvider = conversationMapperProvider;
    this.memberUpdateMapperProvider = memberUpdateMapperProvider;
    this.conversationMemberMapperProvider = conversationMemberMapperProvider;
    this.messageMapperProvider = messageMapperProvider;
    this.messageWithContentMapperProvider = messageWithContentMapperProvider;
  }

  @Override
  public RealChatRepository get() {
    return newInstance(userManagerProvider.get(), serviceProvider.get(), roomMapperProvider.get(), roomWithMemberCountMapperProvider.get(), roomWithMembersMapperProvider.get(), conversationMapperProvider.get(), memberUpdateMapperProvider.get(), conversationMemberMapperProvider.get(), messageMapperProvider.get(), messageWithContentMapperProvider.get());
  }

  public static RealChatRepository_Factory create(Provider<UserManager> userManagerProvider,
      Provider<ChatService> serviceProvider, Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<RoomWithMemberCountMapper> roomWithMemberCountMapperProvider,
      Provider<RoomWithMembersMapper> roomWithMembersMapperProvider,
      Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<MemberUpdateMapper> memberUpdateMapperProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<LastMessageMapper> messageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    return new RealChatRepository_Factory(userManagerProvider, serviceProvider, roomMapperProvider, roomWithMemberCountMapperProvider, roomWithMembersMapperProvider, conversationMapperProvider, memberUpdateMapperProvider, conversationMemberMapperProvider, messageMapperProvider, messageWithContentMapperProvider);
  }

  public static RealChatRepository newInstance(UserManager userManager, ChatService service,
      MetadataRoomMapper roomMapper, RoomWithMemberCountMapper roomWithMemberCountMapper,
      RoomWithMembersMapper roomWithMembersMapper, RoomConversationMapper conversationMapper,
      MemberUpdateMapper memberUpdateMapper, ConversationMemberMapper conversationMemberMapper,
      LastMessageMapper messageMapper,
      ConversationMessageWithContentMapper messageWithContentMapper) {
    return new RealChatRepository(userManager, service, roomMapper, roomWithMemberCountMapper, roomWithMembersMapper, conversationMapper, memberUpdateMapper, conversationMemberMapper, messageMapper, messageWithContentMapper);
  }
}
