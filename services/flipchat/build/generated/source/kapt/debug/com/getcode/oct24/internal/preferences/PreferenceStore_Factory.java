package com.getcode.oct24.internal.preferences;

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
public final class PreferenceStore_Factory implements Factory<PreferenceStore> {
  @Override
  public PreferenceStore get() {
    return newInstance();
  }

  public static PreferenceStore_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PreferenceStore newInstance() {
    return new PreferenceStore();
  }

  private static final class InstanceHolder {
    private static final PreferenceStore_Factory INSTANCE = new PreferenceStore_Factory();
  }
}
