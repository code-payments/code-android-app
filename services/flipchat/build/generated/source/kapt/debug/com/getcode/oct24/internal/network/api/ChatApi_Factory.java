package com.getcode.oct24.internal.network.api;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.grpc.ManagedChannel;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("com.getcode.oct24.internal.annotations.FcManagedChannel")
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
public final class ChatApi_Factory implements Factory<ChatApi> {
  private final Provider<ManagedChannel> managedChannelProvider;

  public ChatApi_Factory(Provider<ManagedChannel> managedChannelProvider) {
    this.managedChannelProvider = managedChannelProvider;
  }

  @Override
  public ChatApi get() {
    return newInstance(managedChannelProvider.get());
  }

  public static ChatApi_Factory create(Provider<ManagedChannel> managedChannelProvider) {
    return new ChatApi_Factory(managedChannelProvider);
  }

  public static ChatApi newInstance(ManagedChannel managedChannel) {
    return new ChatApi(managedChannel);
  }
}
