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
public final class RoomWithMembersMapper_Factory implements Factory<RoomWithMembersMapper> {
  private final Provider<MetadataRoomMapper> roomMapperProvider;

  private final Provider<MemberMapper> memberMapperProvider;

  public RoomWithMembersMapper_Factory(Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<MemberMapper> memberMapperProvider) {
    this.roomMapperProvider = roomMapperProvider;
    this.memberMapperProvider = memberMapperProvider;
  }

  @Override
  public RoomWithMembersMapper get() {
    return newInstance(roomMapperProvider.get(), memberMapperProvider.get());
  }

  public static RoomWithMembersMapper_Factory create(
      Provider<MetadataRoomMapper> roomMapperProvider,
      Provider<MemberMapper> memberMapperProvider) {
    return new RoomWithMembersMapper_Factory(roomMapperProvider, memberMapperProvider);
  }

  public static RoomWithMembersMapper newInstance(MetadataRoomMapper roomMapper,
      MemberMapper memberMapper) {
    return new RoomWithMembersMapper(roomMapper, memberMapper);
  }
}
