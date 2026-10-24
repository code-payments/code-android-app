package com.getcode.opencode.inject

import android.content.Context
import com.getcode.opencode.ProtocolConfig
import com.getcode.opencode.internal.annotations.OpenCodeManagedChannel
import com.getcode.opencode.internal.annotations.OpenCodeManagedStreamingChannel
import com.getcode.opencode.internal.annotations.OpenCodeProtocol
import com.getcode.opencode.utils.logging.LoggingClientInterceptor
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.android.AndroidChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object OpenCodeChannelModule {
    @Singleton
    @OpenCodeProtocol
    @Provides
    fun providesOpenCodeProtocolConfig(
        @ApplicationContext context: Context
    ): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = "ocp-v2.api.flipcash-infra.net"
            override val userAgent: String
                get() {
                    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    return "OpenCodeProtocol/Android/$version"
                }
        }
    }

    @Singleton
    @Provides
    @OpenCodeManagedChannel
    fun provideManagedChannel(
        @ApplicationContext context: Context,
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel {
        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilder.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveWithoutCalls(false)
            .idleTimeout(5, TimeUnit.MINUTES) // drop idle connections
            .intercept(LoggingClientInterceptor())
            .build()
            .also { observeChannelState("opencode", it) }
    }

    @Singleton
    @Provides
    @OpenCodeManagedStreamingChannel
    fun provideManagedStreamingChannel(
        @ApplicationContext context: Context,
        @OpenCodeProtocol
        config: ProtocolConfig,
    ): ManagedChannel {
        return AndroidChannelBuilder
            .usingBuilder(OkHttpChannelBuilder.forAddress(config.baseUrl, config.port))
            .context(context)
            .userAgent(config.userAgent)
            .keepAliveWithoutCalls(true)
            .intercept(LoggingClientInterceptor())
            .build()
            .also { observeChannelState("opencode-stream", it) }
    }

    private fun observeChannelState(name: String, channel: ManagedChannel) {
        val state = channel.getState(false)
        trace(
            tag = "gRPC",
            message = "$name => $state",
            type = TraceType.StateChange,
        )
        if (state != ConnectivityState.SHUTDOWN) {
            channel.notifyWhenStateChanged(state) {
                observeChannelState(name, channel)
            }
        }
    }
}
