package com.getcode.oct24.internal.network.service;

import com.getcode.oct24.internal.network.api.AccountApi;
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
public final class AccountService_Factory implements Factory<AccountService> {
  private final Provider<AccountApi> apiProvider;

  private final Provider<NetworkOracle> networkOracleProvider;

  public AccountService_Factory(Provider<AccountApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    this.apiProvider = apiProvider;
    this.networkOracleProvider = networkOracleProvider;
  }

  @Override
  public AccountService get() {
    return newInstance(apiProvider.get(), networkOracleProvider.get());
  }

  public static AccountService_Factory create(Provider<AccountApi> apiProvider,
      Provider<NetworkOracle> networkOracleProvider) {
    return new AccountService_Factory(apiProvider, networkOracleProvider);
  }

  public static AccountService newInstance(AccountApi api, NetworkOracle networkOracle) {
    return new AccountService(api, networkOracle);
  }
}
