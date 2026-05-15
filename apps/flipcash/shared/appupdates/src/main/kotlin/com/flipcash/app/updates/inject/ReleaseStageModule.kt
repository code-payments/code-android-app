package com.flipcash.app.updates.inject

import android.content.Context
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.updates.internal.GooglePlayReleaseStageProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReleaseStageModule {

    @Provides
    @Singleton
    fun provideReleaseStageProvider(
        @ApplicationContext context: Context,
    ): ReleaseStageProvider = GooglePlayReleaseStageProvider(context)
}
