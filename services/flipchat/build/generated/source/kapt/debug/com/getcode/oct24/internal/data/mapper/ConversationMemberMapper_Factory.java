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
public final class ConversationMemberMapper_Factory implements Factory<ConversationMemberMapper> {
  @Override
  public ConversationMemberMapper get() {
    return newInstance();
  }

  public static ConversationMemberMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ConversationMemberMapper newInstance() {
    return new ConversationMemberMapper();
  }

  private static final class InstanceHolder {
    private static final ConversationMemberMapper_Factory INSTANCE = new ConversationMemberMapper_Factory();
  }
}
