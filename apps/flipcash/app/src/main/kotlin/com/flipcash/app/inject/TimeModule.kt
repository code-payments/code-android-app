package com.flipcash.app.inject

import com.flipcash.app.core.time.TimeProvider
import com.flipcash.app.internal.time.SystemTimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {

    @Provides
    @Singleton
    fun providesTimeProvider(): TimeProvider = SystemTimeProvider()
}
