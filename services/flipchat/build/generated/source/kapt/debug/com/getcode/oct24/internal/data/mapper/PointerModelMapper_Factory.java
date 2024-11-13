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
public final class PointerModelMapper_Factory implements Factory<PointerModelMapper> {
  @Override
  public PointerModelMapper get() {
    return newInstance();
  }

  public static PointerModelMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static PointerModelMapper newInstance() {
    return new PointerModelMapper();
  }

  private static final class InstanceHolder {
    private static final PointerModelMapper_Factory INSTANCE = new PointerModelMapper_Factory();
  }
}
