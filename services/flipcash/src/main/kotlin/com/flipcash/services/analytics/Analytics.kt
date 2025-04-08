package com.flipcash.services.analytics

import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.mixpanel.android.mpmetrics.MixpanelAPI
import javax.inject.Inject

interface FlipcashAnalyticsService : AnalyticsService {
}

class FlipcashAnalyticsManager @Inject constructor(
    private val mixpanelAPI: MixpanelAPI
) : FlipcashAnalyticsService {

    override fun onAppStart() {
    }

    override fun onAppStarted() {
    }

    override fun unintentionalLogout() {
    }

    override fun action(action: AppAction, source: AppActionSource?) {
    }

}