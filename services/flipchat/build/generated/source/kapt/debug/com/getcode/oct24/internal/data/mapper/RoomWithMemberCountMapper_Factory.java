package com.getcode.oct24.internal.data.mapper;

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
public final class RoomWithMemberCountMapper_Factory implements Factory<RoomWithMemberCountMapper> {
  private final Provider<MetadataRoomMapper> roomMapperProvider;

  public RoomWithMemberCountMapper_Factory(Provider<MetadataRoomMapper> roomMapperProvider) {
    this.roomMapperProvider = roomMapperProvider;
  }

  @Override
  public RoomWithMemberCountMapper get() {
    return newInstance(roomMapperProvider.get());
  }

  public static RoomWithMemberCountMapper_Factory create(
      Provider<MetadataRoomMapper> roomMapperProvider) {
    return new RoomWithMemberCountMapper_Factory(roomMapperProvider);
  }

  public static RoomWithMemberCountMapper newInstance(MetadataRoomMapper roomMapper) {
    return new RoomWithMemberCountMapper(roomMapper);
  }
}
