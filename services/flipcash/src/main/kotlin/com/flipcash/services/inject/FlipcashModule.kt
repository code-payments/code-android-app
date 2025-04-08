package com.flipcash.services.inject

import com.getcode.opencode.ProtocolConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object FlipcashModule {
    @Singleton
    @Provides
    fun providesOpenCodeProtocolConfig(): ProtocolConfig {
        return object: ProtocolConfig {
            override val baseUrl: String
                get() = ""
            override val userAgent: String
                get() = "Flipcash/Payments/Android/1.0.0"

        }
    }
}