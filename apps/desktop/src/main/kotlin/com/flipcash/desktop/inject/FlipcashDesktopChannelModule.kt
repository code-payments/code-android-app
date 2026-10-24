package com.flipcash.desktop.inject

import com.flipcash.services.ForegroundGate
import com.flipcash.services.internal.annotations.FlipcashManagedChannel
import com.flipcash.services.internal.annotations.FlipcashManagedStreamingChannel
import com.flipcash.services.internal.annotations.FlipcashProtocol
import com.flipcash.services.user.AnalyticsIdentifier
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import dagger.Module
import dagger.Provides
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Desktop counterpart of the Android `FlipcashChannelModule`: plain okhttp channels with the
 * Android settings, no analytics identity (desktop has no Mixpanel), and a gate that is always open
 * because a desktop process has no background state.
 */
@Module
object FlipcashDesktopChannelModule {
    @Provides
    fun provideAnalyticsIdentifier(): AnalyticsIdentifier = AnalyticsIdentifier { }

    @Provides
    fun providesForegroundGate(): ForegroundGate = ForegroundGate { }

    @Singleton
    @FlipcashProtocol
    @Provides
    fun providesFlipcashProtocolConfig(): ProtocolConfig = object : ProtocolConfig {
        override val baseUrl: String
            get() = "fc-v2.api.flipcash-infra.net"
        override val userAgent: String
            get() = "Flipcash/Desktop/${System.getProperty("os.name")}"
    }

    @Singleton
    @Provides
    @FlipcashManagedChannel
    fun provideManagedChannel(
        @FlipcashProtocol
        config: ProtocolConfig,
    ): ManagedChannel = OkHttpChannelBuilder.forAddress(config.baseUrl, config.port)
        .userAgent(config.userAgent)
        .keepAliveWithoutCalls(false)
        .idleTimeout(5, TimeUnit.MINUTES)
        .intercept(LoggingClientInterceptor())
        .build()

    @Singleton
    @Provides
    @FlipcashManagedStreamingChannel
    fun provideManagedStreamingChannel(
        @FlipcashProtocol
        config: ProtocolConfig,
    ): ManagedChannel = OkHttpChannelBuilder.forAddress(config.baseUrl, config.port)
        .userAgent(config.userAgent)
        .keepAliveTime(config.keepAlive.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .keepAliveTimeout(config.keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .keepAliveWithoutCalls(true)
        .intercept(LoggingClientInterceptor())
        .build()
}
