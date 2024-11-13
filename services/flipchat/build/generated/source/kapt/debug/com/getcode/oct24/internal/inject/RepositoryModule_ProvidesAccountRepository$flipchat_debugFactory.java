package com.getcode.oct24.internal.inject;

import com.getcode.oct24.internal.network.repository.accounts.AccountRepository;
import com.getcode.oct24.internal.network.service.AccountService;
import com.getcode.oct24.user.UserManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class RepositoryModule_ProvidesAccountRepository$flipchat_debugFactory implements Factory<AccountRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<AccountService> serviceProvider;

  public RepositoryModule_ProvidesAccountRepository$flipchat_debugFactory(
      Provider<UserManager> userManagerProvider, Provider<AccountService> serviceProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
  }

  @Override
  public AccountRepository get() {
    return providesAccountRepository$flipchat_debug(userManagerProvider.get(), serviceProvider.get());
  }

  public static RepositoryModule_ProvidesAccountRepository$flipchat_debugFactory create(
      Provider<UserManager> userManagerProvider, Provider<AccountService> serviceProvider) {
    return new RepositoryModule_ProvidesAccountRepository$flipchat_debugFactory(userManagerProvider, serviceProvider);
  }

  public static AccountRepository providesAccountRepository$flipchat_debug(UserManager userManager,
      AccountService service) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.providesAccountRepository$flipchat_debug(userManager, service));
  }
}
