package com.getcode.oct24.internal.inject;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import io.grpc.ManagedChannel;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata({
    "com.getcode.oct24.internal.annotations.FcDevManagedChannel",
    "dagger.hilt.android.qualifiers.ApplicationContext"
})
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
public final class FcApiModule_ProvideDevManagedChannelFactory implements Factory<ManagedChannel> {
  private final Provider<Context> contextProvider;

  public FcApiModule_ProvideDevManagedChannelFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public ManagedChannel get() {
    return provideDevManagedChannel(contextProvider.get());
  }

  public static FcApiModule_ProvideDevManagedChannelFactory create(
      Provider<Context> contextProvider) {
    return new FcApiModule_ProvideDevManagedChannelFactory(contextProvider);
  }

  public static ManagedChannel provideDevManagedChannel(Context context) {
    return Preconditions.checkNotNullFromProvides(FcApiModule.INSTANCE.provideDevManagedChannel(context));
  }
}
