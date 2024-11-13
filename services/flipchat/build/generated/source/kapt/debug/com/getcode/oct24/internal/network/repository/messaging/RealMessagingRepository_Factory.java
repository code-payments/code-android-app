package com.getcode.oct24.internal.network.repository.messaging;

import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper;
import com.getcode.oct24.internal.data.mapper.ChatMessageMapper;
import com.getcode.oct24.internal.data.mapper.LastMessageMapper;
import com.getcode.oct24.internal.network.service.MessagingService;
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
public final class RealMessagingRepository_Factory implements Factory<RealMessagingRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<MessagingService> serviceProvider;

  private final Provider<ChatMessageMapper> messageMapperProvider;

  private final Provider<LastMessageMapper> lastMessageMapperProvider;

  private final Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider;

  public RealMessagingRepository_Factory(Provider<UserManager> userManagerProvider,
      Provider<MessagingService> serviceProvider, Provider<ChatMessageMapper> messageMapperProvider,
      Provider<LastMessageMapper> lastMessageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.messageMapperProvider = messageMapperProvider;
    this.lastMessageMapperProvider = lastMessageMapperProvider;
    this.messageWithContentMapperProvider = messageWithContentMapperProvider;
  }

  @Override
  public RealMessagingRepository get() {
    return newInstance(userManagerProvider.get(), serviceProvider.get(), messageMapperProvider.get(), lastMessageMapperProvider.get(), messageWithContentMapperProvider.get());
  }

  public static RealMessagingRepository_Factory create(Provider<UserManager> userManagerProvider,
      Provider<MessagingService> serviceProvider, Provider<ChatMessageMapper> messageMapperProvider,
      Provider<LastMessageMapper> lastMessageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    return new RealMessagingRepository_Factory(userManagerProvider, serviceProvider, messageMapperProvider, lastMessageMapperProvider, messageWithContentMapperProvider);
  }

  public static RealMessagingRepository newInstance(UserManager userManager,
      MessagingService service, ChatMessageMapper messageMapper,
      LastMessageMapper lastMessageMapper,
      ConversationMessageWithContentMapper messageWithContentMapper) {
    return new RealMessagingRepository(userManager, service, messageMapper, lastMessageMapper, messageWithContentMapper);
  }
}
