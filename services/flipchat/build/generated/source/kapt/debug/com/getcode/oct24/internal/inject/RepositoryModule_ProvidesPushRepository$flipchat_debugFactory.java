package com.getcode.oct24.internal.inject;

import com.getcode.oct24.internal.network.repository.push.PushRepository;
import com.getcode.oct24.internal.network.service.PushService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class RepositoryModule_ProvidesPushRepository$flipchat_debugFactory implements Factory<PushRepository> {
  private final Provider<PushService> serviceProvider;

  public RepositoryModule_ProvidesPushRepository$flipchat_debugFactory(
      Provider<PushService> serviceProvider) {
    this.serviceProvider = serviceProvider;
  }

  @Override
  public PushRepository get() {
    return providesPushRepository$flipchat_debug(serviceProvider.get());
  }

  public static RepositoryModule_ProvidesPushRepository$flipchat_debugFactory create(
      Provider<PushService> serviceProvider) {
    return new RepositoryModule_ProvidesPushRepository$flipchat_debugFactory(serviceProvider);
  }

  public static PushRepository providesPushRepository$flipchat_debug(PushService service) {
    return Preconditions.checkNotNullFromProvides(RepositoryModule.INSTANCE.providesPushRepository$flipchat_debug(service));
  }
}
