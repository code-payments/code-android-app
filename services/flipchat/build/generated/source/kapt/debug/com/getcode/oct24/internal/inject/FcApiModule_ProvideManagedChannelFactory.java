package com.getcode.oct24.internal.inject;

import android.content.Context;
import com.getcode.oct24.FlipchatServicesConfig;
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
    "com.getcode.oct24.internal.annotations.FcManagedChannel",
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
public final class FcApiModule_ProvideManagedChannelFactory implements Factory<ManagedChannel> {
  private final Provider<Context> contextProvider;

  private final Provider<FlipchatServicesConfig> configProvider;

  public FcApiModule_ProvideManagedChannelFactory(Provider<Context> contextProvider,
      Provider<FlipchatServicesConfig> configProvider) {
    this.contextProvider = contextProvider;
    this.configProvider = configProvider;
  }

  @Override
  public ManagedChannel get() {
    return provideManagedChannel(contextProvider.get(), configProvider.get());
  }

  public static FcApiModule_ProvideManagedChannelFactory create(Provider<Context> contextProvider,
      Provider<FlipchatServicesConfig> configProvider) {
    return new FcApiModule_ProvideManagedChannelFactory(contextProvider, configProvider);
  }

  public static ManagedChannel provideManagedChannel(Context context,
      FlipchatServicesConfig config) {
    return Preconditions.checkNotNullFromProvides(FcApiModule.INSTANCE.provideManagedChannel(context, config));
  }
}
