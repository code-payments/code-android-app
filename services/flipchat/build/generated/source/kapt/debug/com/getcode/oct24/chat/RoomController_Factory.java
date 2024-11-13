package com.getcode.oct24.chat;

import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper;
import com.getcode.oct24.internal.data.mapper.ConversationMemberMapper;
import com.getcode.oct24.internal.network.repository.chat.ChatRepository;
import com.getcode.oct24.internal.network.repository.messaging.MessagingRepository;
import com.getcode.oct24.user.UserManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class RoomController_Factory implements Factory<RoomController> {
  private final Provider<ChatRepository> chatRepositoryProvider;

  private final Provider<MessagingRepository> messagingRepositoryProvider;

  private final Provider<UserManager> userManagerProvider;

  private final Provider<ConversationMemberMapper> conversationMemberMapperProvider;

  private final Provider<ConversationMessageWithContentMapper> conversationMessageWithContentMapperProvider;

  public RoomController_Factory(Provider<ChatRepository> chatRepositoryProvider,
      Provider<MessagingRepository> messagingRepositoryProvider,
      Provider<UserManager> userManagerProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<ConversationMessageWithContentMapper> conversationMessageWithContentMapperProvider) {
    this.chatRepositoryProvider = chatRepositoryProvider;
    this.messagingRepositoryProvider = messagingRepositoryProvider;
    this.userManagerProvider = userManagerProvider;
    this.conversationMemberMapperProvider = conversationMemberMapperProvider;
    this.conversationMessageWithContentMapperProvider = conversationMessageWithContentMapperProvider;
  }

  @Override
  public RoomController get() {
    return newInstance(chatRepositoryProvider.get(), messagingRepositoryProvider.get(), userManagerProvider.get(), conversationMemberMapperProvider.get(), conversationMessageWithContentMapperProvider.get());
  }

  public static RoomController_Factory create(Provider<ChatRepository> chatRepositoryProvider,
      Provider<MessagingRepository> messagingRepositoryProvider,
      Provider<UserManager> userManagerProvider,
      Provider<ConversationMemberMapper> conversationMemberMapperProvider,
      Provider<ConversationMessageWithContentMapper> conversationMessageWithContentMapperProvider) {
    return new RoomController_Factory(chatRepositoryProvider, messagingRepositoryProvider, userManagerProvider, conversationMemberMapperProvider, conversationMessageWithContentMapperProvider);
  }

  public static RoomController newInstance(ChatRepository chatRepository,
      MessagingRepository messagingRepository, UserManager userManager,
      ConversationMemberMapper conversationMemberMapper,
      ConversationMessageWithContentMapper conversationMessageWithContentMapper) {
    return new RoomController(chatRepository, messagingRepository, userManager, conversationMemberMapper, conversationMessageWithContentMapper);
  }
}
