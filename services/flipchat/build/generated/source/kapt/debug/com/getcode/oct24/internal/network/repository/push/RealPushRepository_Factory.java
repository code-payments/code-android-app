package com.getcode.oct24.internal.network.repository.push;

import com.getcode.oct24.internal.network.service.PushService;
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
public final class RealPushRepository_Factory implements Factory<RealPushRepository> {
  private final Provider<PushService> serviceProvider;

  public RealPushRepository_Factory(Provider<PushService> serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public RealPushRepository get() {
    return newInstance(serviceProvider.get());
  }

  public static RealPushRepository_Factory create(Provider<PushService> serviceProvider) {
    return new RealPushRepository_Factory(serviceProvider);
  }

  public static RealPushRepository newInstance(PushService service) {
    return new RealPushRepository(service);
  }
}
