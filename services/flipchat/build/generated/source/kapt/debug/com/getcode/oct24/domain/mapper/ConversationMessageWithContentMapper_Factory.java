package com.getcode.oct24.domain.mapper;

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
public final class ConversationMessageWithContentMapper_Factory implements Factory<ConversationMessageWithContentMapper> {
  private final Provider<ConversationMessageMapper> messageMapperProvider;

  public ConversationMessageWithContentMapper_Factory(
      Provider<ConversationMessageMapper> messageMapperProvider) {
    this.messageMapperProvider = messageMapperProvider;
  }

  @Override
  public ConversationMessageWithContentMapper get() {
    return newInstance(messageMapperProvider.get());
  }

  public static ConversationMessageWithContentMapper_Factory create(
      Provider<ConversationMessageMapper> messageMapperProvider) {
    return new ConversationMessageWithContentMapper_Factory(messageMapperProvider);
  }

  public static ConversationMessageWithContentMapper newInstance(
      ConversationMessageMapper messageMapper) {
    return new ConversationMessageWithContentMapper(messageMapper);
  }
}
