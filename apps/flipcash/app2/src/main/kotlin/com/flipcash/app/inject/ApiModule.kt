package com.flipcash.app.inject

import android.content.Context
import com.flipcash.app.android.BuildConfig
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Singleton
    @Provides
    fun provideMixpanelApi(@ApplicationContext context: Context): MixpanelAPI {
        return MixpanelAPI.getInstance(
            /* context = */ context,
            /* token = */ BuildConfig.MIXPANEL_API_KEY,
            /* trackAutomaticEvents = */ true
        )
    }
}