package com.getcode.oct24.domain.mapper;

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
public final class ConversationMessageMapper_Factory implements Factory<ConversationMessageMapper> {
  @Override
  public ConversationMessageMapper get() {
    return newInstance();
  }

  public static ConversationMessageMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ConversationMessageMapper newInstance() {
    return new ConversationMessageMapper();
  }

  private static final class InstanceHolder {
    private static final ConversationMessageMapper_Factory INSTANCE = new ConversationMessageMapper_Factory();
  }
}
