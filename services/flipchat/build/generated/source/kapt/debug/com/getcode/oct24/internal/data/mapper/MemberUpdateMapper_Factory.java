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
public final class MemberUpdateMapper_Factory implements Factory<MemberUpdateMapper> {
  private final Provider<MemberMapper> memberMapperProvider;

  public MemberUpdateMapper_Factory(Provider<MemberMapper> memberMapperProvider) {
    this.memberMapperProvider = memberMapperProvider;
  }

  @Override
  public MemberUpdateMapper get() {
    return newInstance(memberMapperProvider.get());
  }

  public static MemberUpdateMapper_Factory create(Provider<MemberMapper> memberMapperProvider) {
    return new MemberUpdateMapper_Factory(memberMapperProvider);
  }

  public static MemberUpdateMapper newInstance(MemberMapper memberMapper) {
    return new MemberUpdateMapper(memberMapper);
  }
}
