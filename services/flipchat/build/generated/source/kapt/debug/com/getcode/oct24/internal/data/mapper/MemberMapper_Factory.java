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
public final class MemberMapper_Factory implements Factory<MemberMapper> {
  private final Provider<MemberIdentityMapper> identityMapperProvider;

  private final Provider<PointerModelMapper> pointerModelMapperProvider;

  public MemberMapper_Factory(Provider<MemberIdentityMapper> identityMapperProvider,
      Provider<PointerModelMapper> pointerModelMapperProvider) {
    this.identityMapperProvider = identityMapperProvider;
    this.pointerModelMapperProvider = pointerModelMapperProvider;
  }

  @Override
  public MemberMapper get() {
    return newInstance(identityMapperProvider.get(), pointerModelMapperProvider.get());
  }

  public static MemberMapper_Factory create(Provider<MemberIdentityMapper> identityMapperProvider,
      Provider<PointerModelMapper> pointerModelMapperProvider) {
    return new MemberMapper_Factory(identityMapperProvider, pointerModelMapperProvider);
  }

  public static MemberMapper newInstance(MemberIdentityMapper identityMapper,
      PointerModelMapper pointerModelMapper) {
    return new MemberMapper(identityMapper, pointerModelMapper);
  }
}
