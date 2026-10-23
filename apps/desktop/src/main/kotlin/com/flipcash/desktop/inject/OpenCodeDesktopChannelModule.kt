package com.flipcash.desktop.inject

import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeManagedStreamingChannel
import com.getcode.opencode.internal.annotations.OpenCodeProtocol
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import dagger.Module
import dagger.Provides
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Desktop counterpart of the Android `OpenCodeChannelModule`: the same channel settings, built on
 * plain okhttp instead of `AndroidChannelBuilder` (which only adds Android network-change hooks).
 */
@Module
object OpenCodeDesktopChannelModule {
    @Singleton
    @OpenCodeProtocol
    @Provides
    fun providesOpenCodeProtocolConfig(): ProtocolConfig = object : ProtocolConfig {
        override val baseUrl: String
            get() = "ocp-v2.api.flipcash-infra.net"
        override val userAgent: String
            get() = "OpenCodeProtocol/Desktop/${System.getProperty("os.name")}"
    }

    @Singleton
    @Provides
    @OpenCodeManagedChannel
    fun provideManagedChannel(
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel = OkHttpChannelBuilder.forAddress(config.baseUrl, config.port)
        .userAgent(config.userAgent)
        .keepAliveWithoutCalls(false)
        .idleTimeout(5, TimeUnit.MINUTES)
        .intercept(LoggingClientInterceptor())
        .build()

    @Singleton
    @Provides
    @OpenCodeManagedStreamingChannel
    fun provideManagedStreamingChannel(
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel = OkHttpChannelBuilder.forAddress(config.baseUrl, config.port)
        .userAgent(config.userAgent)
        .keepAliveWithoutCalls(true)
        .intercept(LoggingClientInterceptor())
        .build()
}
