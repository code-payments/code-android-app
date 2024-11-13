package com.getcode.oct24.internal.network.service;

import com.getcode.oct24.internal.network.api.MessagingApi;
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
public final class MessagingService_Factory implements Factory<MessagingService> {
  private final Provider<MessagingApi> apiProvider;

  private final Provider<NetworkOracle> networkOracleProvider;

  public MessagingService_Factory(Provider<MessagingApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    this.apiProvider = apiProvider;
    this.networkOracleProvider = networkOracleProvider;
  }

  @Override
  public MessagingService get() {
    return newInstance(apiProvider.get(), networkOracleProvider.get());
  }

  public static MessagingService_Factory create(Provider<MessagingApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    return new MessagingService_Factory(apiProvider, networkOracleProvider);
  }

  public static MessagingService newInstance(MessagingApi api, NetworkOracle networkOracle) {
    return new MessagingService(api, networkOracle);
  }
}
