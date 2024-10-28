package com.getcode.oct24.internal.inject

import android.content.Context
import com.getcode.oct24.internal.annotations.FcDevManagedChannel
import com.getcode.oct24.internal.annotations.FcManagedChannel
import com.getcode.oct24.internal.annotations.FcNetworkOracle
import com.getcode.oct24.internal.network.core.NetworkOracle
import com.getcode.oct24.internal.network.core.NetworkOracleImpl
import com.getcode.oct24.internal.network.utils.logging.LoggingClientInterceptor
import com.getcode.oct24.services.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import org.kin.sdk.base.network.api.agora.OkHttpChannelBuilderForcedTls12
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FcApiModule {
    @FcNetworkOracle
    @Provides
    fun provideNetworkOracle(): NetworkOracle {
        return NetworkOracleImpl()
    }

    @Singleton
    @Provides
    @FcManagedChannel
    fun provideManagedChannel(@ApplicationContext context: Context): ManagedChannel {
        val TLS_PORT = 443
        val PROD_URL = "api.flipchat.codeinfra.net"

        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(PROD_URL, TLS_PORT))
            .context(context)
            .userAgent("Flipchat/Android/${BuildConfig.VERSION_NAME}")
            .keepAliveTime(4, TimeUnit.MINUTES)
            .intercept(LoggingClientInterceptor())
            .build()
    }

    @Singleton
    @Provides
    @FcDevManagedChannel
    fun provideDevManagedChannel(@ApplicationContext context: Context): ManagedChannel {
        val TLS_PORT = 443
        val DEV_URL = "api.flipchat.codeinfra.dev"

        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilderForcedTls12.forAddress(DEV_URL, TLS_PORT))
            .context(context)
            .userAgent("Flipchat/Android/${BuildConfig.VERSION_NAME}")
            .keepAliveTime(4, TimeUnit.MINUTES)
            .build()
    }
}