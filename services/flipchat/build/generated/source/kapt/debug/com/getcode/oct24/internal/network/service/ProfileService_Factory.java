package com.getcode.oct24.internal.network.service;

import com.getcode.oct24.internal.network.api.ProfileApi;
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
public final class ProfileService_Factory implements Factory<ProfileService> {
  private final Provider<ProfileApi> apiProvider;

  private final Provider<NetworkOracle> networkOracleProvider;

  public ProfileService_Factory(Provider<ProfileApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    this.apiProvider = apiProvider;
    this.networkOracleProvider = networkOracleProvider;
  }

  @Override
  public ProfileService get() {
    return newInstance(apiProvider.get(), networkOracleProvider.get());
  }

  public static ProfileService_Factory create(Provider<ProfileApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    return new ProfileService_Factory(apiProvider, networkOracleProvider);
  }

  public static ProfileService newInstance(ProfileApi api, NetworkOracle networkOracle) {
    return new ProfileService(api, networkOracle);
  }
}
