package com.flipcash.app.inject

import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.app.internal.dispatchers.DefaultDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    fun providesDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
