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
public final class MetadataRoomMapper_Factory implements Factory<MetadataRoomMapper> {
  @Override
  public MetadataRoomMapper get() {
    return newInstance();
  }

  public static MetadataRoomMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MetadataRoomMapper newInstance() {
    return new MetadataRoomMapper();
  }

  private static final class InstanceHolder {
    private static final MetadataRoomMapper_Factory INSTANCE = new MetadataRoomMapper_Factory();
  }
}
