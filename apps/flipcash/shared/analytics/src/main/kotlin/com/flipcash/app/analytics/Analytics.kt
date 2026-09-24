package com.flipcash.app.analytics

import androidx.compose.runtime.Composable
import com.flipcash.app.core.DisplayNameSource
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.opencode.model.financial.CurrencyCode

interface FlipcashAnalyticsService : AnalyticsService, FlipcashAnalytics {
    fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair)
    fun displayedErrorModal(title: String, message: String, screen: String? = null, callSite: String? = null)

    /** @param hadPreviousName true when the user is replacing a name, false on first set. */
    fun displayNameSubmitted(source: DisplayNameSource, hadPreviousName: Boolean)

    fun buttonTapped(button: Button) {
        action(button)
    }
}

class StubFlipcashAnalytics : FlipcashAnalyticsService {
    override fun track(event: com.flipcash.analytics.AnalyticsEvent) = Unit
    override fun increment(counter: com.flipcash.analytics.PeopleCounter, amount: Double) = Unit
    override fun onAppStart() = Unit
    override fun onAppStarted() = Unit
    override fun unintentionalLogout() = Unit
    override fun action(action: AppAction, source: AppActionSource?) = Unit

    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) = Unit

    override fun displayedErrorModal(title: String, message: String, screen: String?, callSite: String?) = Unit
    override fun displayNameSubmitted(source: DisplayNameSource, hadPreviousName: Boolean) = Unit
}

@Composable
fun rememberAnalytics(): FlipcashAnalyticsService {
    return LocalAnalytics.current as FlipcashAnalyticsService
}