package com.getcode.oct24.internal.inject;

import com.getcode.oct24.domain.mapper.ConversationMessageWithContentMapper;
import com.getcode.oct24.internal.data.mapper.ChatMessageMapper;
import com.getcode.oct24.internal.data.mapper.LastMessageMapper;
import com.getcode.oct24.internal.network.repository.messaging.MessagingRepository;
import com.getcode.oct24.internal.network.service.MessagingService;
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
public final class RepositoryModule_ProvidesMessagingRepository$flipchat_debugFactory implements Factory<MessagingRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<MessagingService> serviceProvider;

  private final Provider<ChatMessageMapper> messageMapperProvider;

  private final Provider<LastMessageMapper> lastMessageMapperProvider;

  private final Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider;

  public RepositoryModule_ProvidesMessagingRepository$flipchat_debugFactory(
      Provider<UserManager> userManagerProvider, Provider<MessagingService> serviceProvider,
      Provider<ChatMessageMapper> messageMapperProvider,
      Provider<LastMessageMapper> lastMessageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.messageMapperProvider = messageMapperProvider;
    this.lastMessageMapperProvider = lastMessageMapperProvider;
    this.messageWithContentMapperProvider = messageWithContentMapperProvider;
  }

  @Override
  public MessagingRepository get() {
    return providesMessagingRepository$flipchat_debug(userManagerProvider.get(), serviceProvider.get(), messageMapperProvider.get(), lastMessageMapperProvider.get(), messageWithContentMapperProvider.get());
  }

  public static RepositoryModule_ProvidesMessagingRepository$flipchat_debugFactory create(
      Provider<UserManager> userManagerProvider, Provider<MessagingService> serviceProvider,
      Provider<ChatMessageMapper> messageMapperProvider,
      Provider<LastMessageMapper> lastMessageMapperProvider,
      Provider<ConversationMessageWithContentMapper> messageWithContentMapperProvider) {
    return new RepositoryModule_ProvidesMessagingRepository$flipchat_debugFactory(userManagerProvider, serviceProvider, messageMapperProvider, lastMessageMapperProvider, messageWithContentMapperProvider);
  }

  public static MessagingRepository providesMessagingRepository$flipchat_debug(
      UserManager userManager, MessagingService service, ChatMessageMapper messageMapper,
      LastMessageMapper lastMessageMapper,
      ConversationMessageWithContentMapper messageWithContentMapper) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.providesMessagingRepository$flipchat_debug(userManager, service, messageMapper, lastMessageMapper, messageWithContentMapper));
  }
}
