package com.getcode.oct24.network.controllers;

import com.getcode.oct24.internal.network.repository.profile.ProfileRepository;
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
public final class ProfileController_Factory implements Factory<ProfileController> {
  private final Provider<ProfileRepository> repositoryProvider;

  public ProfileController_Factory(Provider<ProfileRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public ProfileController get() {
    return newInstance(repositoryProvider.get());
  }

  public static ProfileController_Factory create(Provider<ProfileRepository> repositoryProvider) {
    return new ProfileController_Factory(repositoryProvider);
  }

  public static ProfileController newInstance(ProfileRepository repository) {
    return new ProfileController(repository);
  }
}
