package com.getcode.oct24.network.controllers;

import com.getcode.oct24.domain.mapper.RoomConversationMapper;
import com.getcode.oct24.internal.network.repository.chat.ChatRepository;
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
public final class ChatsController_Factory implements Factory<ChatsController> {
  private final Provider<RoomConversationMapper> conversationMapperProvider;

  private final Provider<ChatRepository> repositoryProvider;

  public ChatsController_Factory(Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<ChatRepository> repositoryProvider) {
    this.conversationMapperProvider = conversationMapperProvider;
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ChatsController get() {
    return newInstance(conversationMapperProvider.get(), repositoryProvider.get());
  }

  public static ChatsController_Factory create(
      Provider<RoomConversationMapper> conversationMapperProvider,
      Provider<ChatRepository> repositoryProvider) {
    return new ChatsController_Factory(conversationMapperProvider, repositoryProvider);
  }

  public static ChatsController newInstance(RoomConversationMapper conversationMapper,
      ChatRepository repository) {
    return new ChatsController(conversationMapper, repository);
  }
}
