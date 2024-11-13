package com.getcode.oct24.network.controllers;

import com.getcode.oct24.internal.network.repository.push.PushRepository;
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
public final class PushController_Factory implements Factory<PushController> {
  private final Provider<PushRepository> repositoryProvider;

  public PushController_Factory(Provider<PushRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public PushController get() {
    return newInstance(repositoryProvider.get());
  }

  public static PushController_Factory create(Provider<PushRepository> repositoryProvider) {
    return new PushController_Factory(repositoryProvider);
  }

  public static PushController newInstance(PushRepository repository) {
    return new PushController(repository);
  }
}
