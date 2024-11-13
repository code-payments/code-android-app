package com.getcode.oct24.internal.inject;

import com.getcode.oct24.internal.data.mapper.ProfileMapper;
import com.getcode.oct24.internal.network.repository.profile.ProfileRepository;
import com.getcode.oct24.internal.network.service.ProfileService;
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
public final class RepositoryModule_ProvidesProfileRepository$flipchat_debugFactory implements Factory<ProfileRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<ProfileService> serviceProvider;

  private final Provider<ProfileMapper> profileMapperProvider;

  public RepositoryModule_ProvidesProfileRepository$flipchat_debugFactory(
      Provider<UserManager> userManagerProvider, Provider<ProfileService> serviceProvider,
      Provider<ProfileMapper> profileMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.profileMapperProvider = profileMapperProvider;
  }

  @Override
  public ProfileRepository get() {
    return providesProfileRepository$flipchat_debug(userManagerProvider.get(), serviceProvider.get(), profileMapperProvider.get());
  }

  public static RepositoryModule_ProvidesProfileRepository$flipchat_debugFactory create(
      Provider<UserManager> userManagerProvider, Provider<ProfileService> serviceProvider,
      Provider<ProfileMapper> profileMapperProvider) {
    return new RepositoryModule_ProvidesProfileRepository$flipchat_debugFactory(userManagerProvider, serviceProvider, profileMapperProvider);
  }

  public static ProfileRepository providesProfileRepository$flipchat_debug(UserManager userManager,
      ProfileService service, ProfileMapper profileMapper) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.providesProfileRepository$flipchat_debug(userManager, service, profileMapper));
  }
}
