package com.getcode.oct24.internal.inject;

import com.getcode.oct24.internal.network.core.NetworkOracle;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata("com.getcode.oct24.internal.annotations.FcNetworkOracle")
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
public final class FcApiModule_ProvideNetworkOracleFactory implements Factory<NetworkOracle> {
  @Override
  public NetworkOracle get() {
    return provideNetworkOracle();
  }

  public static FcApiModule_ProvideNetworkOracleFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static NetworkOracle provideNetworkOracle() {
    return Preconditions.checkNotNullFromProvides(FcApiModule.INSTANCE.provideNetworkOracle());
  }

  private static final class InstanceHolder {
    private static final FcApiModule_ProvideNetworkOracleFactory INSTANCE = new FcApiModule_ProvideNetworkOracleFactory();
  }
}
