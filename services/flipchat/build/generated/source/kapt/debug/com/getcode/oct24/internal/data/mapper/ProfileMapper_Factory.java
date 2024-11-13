package com.getcode.oct24.internal.data.mapper;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class ProfileMapper_Factory implements Factory<ProfileMapper> {
  @Override
  public ProfileMapper get() {
    return newInstance();
  }

  public static ProfileMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ProfileMapper newInstance() {
    return new ProfileMapper();
  }

  private static final class InstanceHolder {
    private static final ProfileMapper_Factory INSTANCE = new ProfileMapper_Factory();
  }
}
