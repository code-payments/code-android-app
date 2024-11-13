package com.getcode.oct24.internal.data.mapper;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class ChatMessageMapper_Factory implements Factory<ChatMessageMapper> {
  @Override
  public ChatMessageMapper get() {
    return newInstance();
  }

  public static ChatMessageMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ChatMessageMapper newInstance() {
    return new ChatMessageMapper();
  }

  private static final class InstanceHolder {
    private static final ChatMessageMapper_Factory INSTANCE = new ChatMessageMapper_Factory();
  }
}
