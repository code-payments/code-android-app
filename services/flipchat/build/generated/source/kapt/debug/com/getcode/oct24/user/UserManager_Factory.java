package com.getcode.oct24.user;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class UserManager_Factory implements Factory<UserManager> {
  @Override
  public UserManager get() {
    return newInstance();
  }

  public static UserManager_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static UserManager newInstance() {
    return new UserManager();
  }

  private static final class InstanceHolder {
    private static final UserManager_Factory INSTANCE = new UserManager_Factory();
  }
}
