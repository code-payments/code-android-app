package com.flipcash.app.onramp.inject

import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.onramp.OnRampAmountController
import com.flipcash.app.onramp.internal.InternalOnRampAmountController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnRampAmountModule {

    @Provides
    @Singleton
    fun providesOnRampAmountController(
        analytics: FlipcashAnalyticsService
    ): OnRampAmountController = InternalOnRampAmountController(analytics)
}