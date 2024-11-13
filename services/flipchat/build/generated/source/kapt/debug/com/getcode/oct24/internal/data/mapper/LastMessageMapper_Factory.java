package com.getcode.oct24.internal.data.mapper;

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
public final class LastMessageMapper_Factory implements Factory<LastMessageMapper> {
  private final Provider<UserManager> userManagerProvider;

  public LastMessageMapper_Factory(Provider<UserManager> userManagerProvider) {
    this.userManagerProvider = userManagerProvider;
  }

  @Override
  public LastMessageMapper get() {
    return newInstance(userManagerProvider.get());
  }

  public static LastMessageMapper_Factory create(Provider<UserManager> userManagerProvider) {
    return new LastMessageMapper_Factory(userManagerProvider);
  }

  public static LastMessageMapper newInstance(UserManager userManager) {
    return new LastMessageMapper(userManager);
  }
}
