package com.flipcash.app.featureflags.inject

import android.content.Context
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.app.featureflags.internal.InternalFeatureFlagController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagModule {
    @Provides
    @Singleton
    fun provideFeatureFlagController(
        @ApplicationContext context: Context,
    ): FeatureFlagController = InternalFeatureFlagController(context)

}