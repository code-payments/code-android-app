package com.flipcash.app.analytics.inject

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.internal.MixpanelAnalyticsDelegate
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI
    ): FlipcashAnalyticsService = MixpanelAnalyticsDelegate(mixpanelAPI)
}