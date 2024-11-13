package com.getcode.oct24.internal.network.service;

import com.getcode.oct24.internal.network.api.PushApi;
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
public final class PushService_Factory implements Factory<PushService> {
  private final Provider<PushApi> apiProvider;

  private final Provider<NetworkOracle> networkOracleProvider;

  public PushService_Factory(Provider<PushApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    this.apiProvider = apiProvider;
    this.networkOracleProvider = networkOracleProvider;
  }

  @Override
  public PushService get() {
    return newInstance(apiProvider.get(), networkOracleProvider.get());
  }

  public static PushService_Factory create(Provider<PushApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    return new PushService_Factory(apiProvider, networkOracleProvider);
  }

  public static PushService newInstance(PushApi api, NetworkOracle networkOracle) {
    return new PushService(api, networkOracle);
  }
}
