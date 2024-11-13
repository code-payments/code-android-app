package com.getcode.oct24.domain.mapper;

import com.getcode.util.resources.ResourceHelper;
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
public final class RoomConversationMapper_Factory implements Factory<RoomConversationMapper> {
  private final Provider<ResourceHelper> resourcesProvider;

  public RoomConversationMapper_Factory(Provider<ResourceHelper> resourcesProvider) {
    this.resourcesProvider = resourcesProvider;
  }

  @Override
  public RoomConversationMapper get() {
    return newInstance(resourcesProvider.get());
  }

  public static RoomConversationMapper_Factory create(Provider<ResourceHelper> resourcesProvider) {
    return new RoomConversationMapper_Factory(resourcesProvider);
  }

  public static RoomConversationMapper newInstance(ResourceHelper resources) {
    return new RoomConversationMapper(resources);
  }
}
