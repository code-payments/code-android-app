package com.flipcash.app.analytics

import androidx.compose.runtime.Composable
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.navigation.DeeplinkType
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Mint

interface FlipcashAnalyticsService : AnalyticsService, FlipcashAnalytics {
    fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair)
    fun buy(method: Analytics.PurchaseMethod, mint: Mint, amount: Fiat, error: Throwable? = null)
    fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable? = null)
    fun tipCardScanned()

    /** An image was picked from the gallery and the still-image search started. */
    fun galleryImagePicked()

    /** @param tier which rung of the crop ladder decoded, 1 to 3. */
    fun galleryScanSucceeded(tier: Int, zoom: Float, timeMillis: Long)

    /** @param exhausted true when the budget ran out rather than the ladder ending. */
    fun galleryScanFailed(timeMillis: Long, exhausted: Boolean)
    fun tipCardPresented()
    fun deeplinkOpened(url: String)
    fun deeplinkParsed(type: DeeplinkType?, url: String)
    fun deeplinkRouted(type: DeeplinkType, error: Throwable? = null)
    fun displayedErrorModal(title: String, message: String, screen: String? = null, callSite: String? = null)

    /** @param hadPreviousName true when the user is replacing a name, false on first set. */
    fun displayNameSubmitted(source: DisplayNameSource, hadPreviousName: Boolean)

    fun buttonTapped(button: Button) {
        action(button)
    }
}

object Analytics {

    enum class PurchaseMethod { Reserves, Phantom, Coinbase }
    sealed interface SwapMethod {
        enum class Buy(val with: PurchaseMethod) : SwapMethod {
            Reserves(PurchaseMethod.Reserves),
            Phantom(PurchaseMethod.Phantom),
            Coinbase(PurchaseMethod.Coinbase)
        }
        data object Sell : SwapMethod
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

    override fun buy(method: Analytics.PurchaseMethod, mint: Mint, amount: Fiat, error: Throwable?) = Unit
    override fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable?) = Unit

    override fun tipCardScanned() = Unit
    override fun galleryImagePicked() = Unit
    override fun galleryScanSucceeded(tier: Int, zoom: Float, timeMillis: Long) = Unit
    override fun galleryScanFailed(timeMillis: Long, exhausted: Boolean) = Unit
    override fun tipCardPresented() = Unit

    override fun deeplinkOpened(url: String) = Unit
    override fun deeplinkParsed(type: DeeplinkType?, url: String) = Unit
    override fun deeplinkRouted(type: DeeplinkType, error: Throwable?) = Unit
    override fun displayedErrorModal(title: String, message: String, screen: String?, callSite: String?) = Unit
    override fun displayNameSubmitted(source: DisplayNameSource, hadPreviousName: Boolean) = Unit
}

@Composable
fun rememberAnalytics(): FlipcashAnalyticsService {
    return LocalAnalytics.current as FlipcashAnalyticsService
}