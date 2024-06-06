package com.getcode.inject

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.bmcreations.tipkit.engines.EventEngine
import dev.bmcreations.tipkit.engines.TipsEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TipModule {
    @Provides
    @Singleton
    fun providesTipEngine(
        eventEngine: EventEngine
    ) = TipsEngine(eventEngine)

    @Singleton
    @Provides
    fun providesEventEngine(
        @ApplicationContext context: Context
    ) = EventEngine(context)
}
