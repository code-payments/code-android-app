package com.getcode.oct24.internal.network.repository.profile;

import com.getcode.oct24.internal.data.mapper.ProfileMapper;
import com.getcode.oct24.internal.network.service.ProfileService;
import com.getcode.oct24.user.UserManager;
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
public final class RealProfileRepository_Factory implements Factory<RealProfileRepository> {
  private final Provider<UserManager> userManagerProvider;

  private final Provider<ProfileService> serviceProvider;

  private final Provider<ProfileMapper> profileMapperProvider;

  public RealProfileRepository_Factory(Provider<UserManager> userManagerProvider,
      Provider<ProfileService> serviceProvider, Provider<ProfileMapper> profileMapperProvider) {
    this.userManagerProvider = userManagerProvider;
    this.serviceProvider = serviceProvider;
    this.profileMapperProvider = profileMapperProvider;
  }

  @Override
  public RealProfileRepository get() {
    return newInstance(userManagerProvider.get(), serviceProvider.get(), profileMapperProvider.get());
  }

  public static RealProfileRepository_Factory create(Provider<UserManager> userManagerProvider,
      Provider<ProfileService> serviceProvider, Provider<ProfileMapper> profileMapperProvider) {
    return new RealProfileRepository_Factory(userManagerProvider, serviceProvider, profileMapperProvider);
  }

  public static RealProfileRepository newInstance(UserManager userManager, ProfileService service,
      ProfileMapper profileMapper) {
    return new RealProfileRepository(userManager, service, profileMapper);
  }
}
