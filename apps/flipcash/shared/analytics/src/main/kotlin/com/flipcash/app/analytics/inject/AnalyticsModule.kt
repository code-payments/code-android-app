package com.flipcash.app.analytics.inject

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.internal.FirebasePaymentFlowTracer
import com.flipcash.app.analytics.internal.MixpanelAnalyticsDelegate
import com.flipcash.app.analytics.internal.PaymentFlowTracer
import com.mixpanel.android.mpmetrics.MixpanelAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    fun providePaymentFlowTracer(): PaymentFlowTracer = FirebasePaymentFlowTracer()

    @Provides
    fun providesAnalyticsService(
        mixpanelAPI: MixpanelAPI,
        flowTracer: PaymentFlowTracer,
    ): FlipcashAnalyticsService = MixpanelAnalyticsDelegate(mixpanelAPI, flowTracer)
}