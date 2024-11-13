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
public final class MemberIdentityMapper_Factory implements Factory<MemberIdentityMapper> {
  @Override
  public MemberIdentityMapper get() {
    return newInstance();
  }

  public static MemberIdentityMapper_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static MemberIdentityMapper newInstance() {
    return new MemberIdentityMapper();
  }

  private static final class InstanceHolder {
    private static final MemberIdentityMapper_Factory INSTANCE = new MemberIdentityMapper_Factory();
  }
}
