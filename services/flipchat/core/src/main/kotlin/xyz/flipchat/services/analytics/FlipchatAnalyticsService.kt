package xyz.flipchat.services.analytics

import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.mixpanel.android.mpmetrics.MixpanelAPI
import javax.inject.Inject


interface FlipchatAnalyticsService : AnalyticsService {
}

class FlipchatAnalyticsManager @Inject constructor(
    private val mixpanelAPI: MixpanelAPI
) : FlipchatAnalyticsService {
    override fun onAppStart() {
    }

    override fun onAppStarted() {
    }

    override fun unintentionalLogout() {
    }

    override fun action(action: AppAction, source: AppActionSource?) {
    }

}