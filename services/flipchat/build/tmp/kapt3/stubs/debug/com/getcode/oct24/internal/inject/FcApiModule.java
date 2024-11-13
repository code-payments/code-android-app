package com.getcode.oct24.internal.inject;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u00c1\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u0003\u001a\u00020\u00042\b\b\u0001\u0010\u0005\u001a\u00020\u0006H\u0007J\u001a\u0010\u0007\u001a\u00020\u00042\b\b\u0001\u0010\u0005\u001a\u00020\u00062\u0006\u0010\b\u001a\u00020\tH\u0007J\b\u0010\n\u001a\u00020\u000bH\u0007\u00a8\u0006\f"}, d2 = {"Lcom/getcode/oct24/internal/inject/FcApiModule;", "", "()V", "provideDevManagedChannel", "Lio/grpc/ManagedChannel;", "context", "Landroid/content/Context;", "provideManagedChannel", "config", "Lcom/getcode/oct24/FlipchatServicesConfig;", "provideNetworkOracle", "Lcom/getcode/oct24/internal/network/core/NetworkOracle;", "flipchat_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public final class FcApiModule {
    @org.jetbrains.annotations.NotNull()
    public static final com.getcode.oct24.internal.inject.FcApiModule INSTANCE = null;
    
    private FcApiModule() {
        super();
    }
    
    @com.getcode.oct24.internal.annotations.FcNetworkOracle()
    @dagger.Provides()
    @org.jetbrains.annotations.NotNull()
    public final com.getcode.oct24.internal.network.core.NetworkOracle provideNetworkOracle() {
        return null;
    }
    
    @javax.inject.Singleton()
    @dagger.Provides()
    @com.getcode.oct24.internal.annotations.FcManagedChannel()
    @org.jetbrains.annotations.NotNull()
    public final io.grpc.ManagedChannel provideManagedChannel(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    com.getcode.oct24.FlipchatServicesConfig config) {
        return null;
    }
    
    @javax.inject.Singleton()
    @dagger.Provides()
    @com.getcode.oct24.internal.annotations.FcDevManagedChannel()
    @org.jetbrains.annotations.NotNull()
    public final io.grpc.ManagedChannel provideDevManagedChannel(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context context) {
        return null;
    }
}