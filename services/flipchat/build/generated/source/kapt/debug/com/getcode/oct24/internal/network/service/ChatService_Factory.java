package com.getcode.oct24.internal.network.service;

import com.getcode.oct24.internal.network.api.ChatApi;
import com.getcode.oct24.internal.network.core.NetworkOracle;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("com.getcode.oct24.internal.annotations.FcNetworkOracle")
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
public final class ChatService_Factory implements Factory<ChatService> {
  private final Provider<ChatApi> apiProvider;

  private final Provider<NetworkOracle> networkOracleProvider;

  public ChatService_Factory(Provider<ChatApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    this.apiProvider = apiProvider;
    this.networkOracleProvider = networkOracleProvider;
  }

  @Override
  public ChatService get() {
    return newInstance(apiProvider.get(), networkOracleProvider.get());
  }

  public static ChatService_Factory create(Provider<ChatApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    return new ChatService_Factory(apiProvider, networkOracleProvider);
  }

  public static ChatService newInstance(ChatApi api, NetworkOracle networkOracle) {
    return new ChatService(api, networkOracle);
  }
}
