package com.getcode.oct24.network.controllers;

import com.getcode.oct24.internal.network.repository.accounts.AccountRepository;
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
public final class AuthController_Factory implements Factory<AuthController> {
  private final Provider<AccountRepository> repositoryProvider;

  public AuthController_Factory(Provider<AccountRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public AuthController get() {
    return newInstance(repositoryProvider.get());
  }

  public static AuthController_Factory create(Provider<AccountRepository> repositoryProvider) {
    return new AuthController_Factory(repositoryProvider);
  }

  public static AuthController newInstance(AccountRepository repository) {
    return new AuthController(repository);
  }
}
