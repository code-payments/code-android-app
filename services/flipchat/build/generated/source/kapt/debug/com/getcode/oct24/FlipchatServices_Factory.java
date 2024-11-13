package com.getcode.oct24;

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
public final class FlipchatServices_Factory implements Factory<FlipchatServices> {
  @Override
  public FlipchatServices get() {
    return newInstance();
  }

  public static FlipchatServices_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FlipchatServices newInstance() {
    return new FlipchatServices();
  }

  private static final class InstanceHolder {
    private static final FlipchatServices_Factory INSTANCE = new FlipchatServices_Factory();
  }
}
