package com.getcode.oct24.internal.network.repository.accounts;

import com.getcode.oct24.internal.network.service.AccountService;
import com.getcode.oct24.user.UserManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class RealAccountRepository_Factory implements Factory<RealAccountRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<AccountService> serviceProvider;

  public RealAccountRepository_Factory(Provider<UserManager> userManagerProvider,
      Provider<AccountService> serviceProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
  }

  @Override
  public RealAccountRepository get() {
    return newInstance(userManagerProvider.get(), serviceProvider.get());
  }

  public static RealAccountRepository_Factory create(Provider<UserManager> userManagerProvider,
      Provider<AccountService> serviceProvider) {
    return new RealAccountRepository_Factory(userManagerProvider, serviceProvider);
  }

  public static RealAccountRepository newInstance(UserManager userManager, AccountService service) {
    return new RealAccountRepository(userManager, service);
  }
}
