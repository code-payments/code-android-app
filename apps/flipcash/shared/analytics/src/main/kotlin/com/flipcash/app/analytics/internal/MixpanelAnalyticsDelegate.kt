package com.flipcash.app.analytics.internal

import com.flipcash.analytics.PeopleCounter
import com.flipcash.analytics.PropertyValue
import com.flipcash.app.analytics.Analytics
import com.flipcash.app.analytics.AnalyticsEvent
import com.flipcash.app.analytics.FlipcashAnalyticsService
import com.flipcash.app.analytics.TokenSymbolResolver
import com.flipcash.app.analytics.asProperties
import com.flipcash.app.analytics.propertyValue
import com.flipcash.app.core.DisplayNameSource
import com.flipcash.app.core.navigation.DeeplinkType
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.services.flipcash.BuildConfig
import com.getcode.solana.keys.Mint
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import javax.inject.Inject

internal class MixpanelAnalyticsDelegate @Inject constructor(
    private val mixpanelAPI: MixpanelAPI,
    private val tokenSymbolResolver: TokenSymbolResolver,
) : FlipcashAnalyticsService {

    private var traceAppInit: Trace? = null
    private var timeAppInit: Long? = null

    override fun onAppStart() {
        timeAppInit = System.currentTimeMillis()
        traceAppInit = Firebase.performance.newTrace("Init")
        traceAppInit?.start()
    }

    override fun onAppStarted() {
        traceAppInit ?: return
        traceAppInit?.stop()
        traceAppInit = null
        val duration = System.currentTimeMillis() - (timeAppInit ?: 0)
        trace(
            tag = "Analytics",
            message = "App started",
            metadata = { "duration" to duration },
            type = TraceType.Process
        )
    }

    override fun unintentionalLogout() = Unit

    override fun track(event: com.flipcash.analytics.AnalyticsEvent) {
        val properties = event.toMixpanelProperties(tokenSymbolResolver)

        if (BuildConfig.DEBUG) {
            val propsString = properties.entries.joinToString { "${it.key} => ${it.value}" }
            trace(
                buildString {
                    append("debug track ${event.name}")
                    if (propsString.isNotEmpty()) append(", $propsString")
                },
                type = TraceType.Silent
            )
            return
        }

        mixpanelAPI.track(event.name, JSONObject(properties))
    }

    override fun increment(counter: PeopleCounter, amount: Double) {
        increment(counter.key, amount)
    }

    override fun action(action: AppAction, source: AppActionSource?) {
        track(name = action.value)
    }

    override fun paidForAccount(price: Double, currency: CurrencyCode, owner: KeyPair) {
        track(AnalyticsEvent.PaidForAccount(price, currency, owner))
    }

    override fun openTokenInfo(source: Analytics.TokenInfoSource, mint: Mint) {
        val event = when (source) {
            Analytics.TokenInfoSource.Deeplink -> AnalyticsEvent.OpenTokenInfoEvent.Deeplink(mint)
            Analytics.TokenInfoSource.Wallet -> AnalyticsEvent.OpenTokenInfoEvent.Wallet(mint)
            Analytics.TokenInfoSource.Give -> AnalyticsEvent.OpenTokenInfoEvent.Give(mint)
        }
        track(event)
    }

    override fun buy(
        method: Analytics.PurchaseMethod,
        mint: Mint,
        amount: Fiat,
        error: Throwable?
    ) {
        val event = when (method) {
            Analytics.PurchaseMethod.Reserves -> AnalyticsEvent.TokenTransactionEvent.Purchase.Reserves(
                mint,
                amount,
                error
            )

            Analytics.PurchaseMethod.Phantom -> AnalyticsEvent.TokenTransactionEvent.Purchase.Phantom(
                mint,
                amount,
                error
            )

            Analytics.PurchaseMethod.Coinbase -> AnalyticsEvent.TokenTransactionEvent.Purchase.Coinbase(
                mint,
                amount,
                error
            )
        }
        track(event)
    }

    override fun sell(mint: Mint, amount: Fiat, feeAmount: Fiat, error: Throwable?) {
        track(AnalyticsEvent.TokenTransactionEvent.Sell(mint, amount, feeAmount, error))
    }

    override fun tipCardScanned() {
        track(AnalyticsEvent.TipCardEvent.Scanned)
    }

    override fun galleryImagePicked() {
        track(AnalyticsEvent.GalleryScanEvent.ImagePicked)
    }

    override fun galleryScanSucceeded(tier: Int, zoom: Float, timeMillis: Long) {
        track(AnalyticsEvent.GalleryScanEvent.Succeeded(tier, zoom, timeMillis))
    }

    override fun galleryScanFailed(timeMillis: Long, exhausted: Boolean) {
        track(AnalyticsEvent.GalleryScanEvent.Failed(timeMillis, exhausted))
    }

    override fun tipCardPresented() {
        track(AnalyticsEvent.TipCardEvent.Presented)
    }

    override fun deeplinkOpened(url: String) {
        track(AnalyticsEvent.DeeplinkEvent.Open(url))
    }

    override fun deeplinkParsed(type: DeeplinkType?, url: String) {
        track(AnalyticsEvent.DeeplinkEvent.Parse(type, url))
    }

    override fun deeplinkRouted(type: DeeplinkType, error: Throwable?) {
        track(AnalyticsEvent.DeeplinkEvent.Routed(type, error))
    }

    override fun displayedErrorModal(title: String, message: String, screen: String?, callSite: String?) {
        track(AnalyticsEvent.ErrorModalDisplayed(title, message, screen, callSite))
    }

    override fun displayNameSubmitted(source: DisplayNameSource, hadPreviousName: Boolean) {
        val event = if (hadPreviousName) {
            AnalyticsEvent.DisplayNameEvent.Updated(source)
        } else {
            AnalyticsEvent.DisplayNameEvent.Set(source)
        }
        track(event)
    }

    // region Internal

    private fun track(event: AnalyticsEvent, vararg extra: Pair<String, String>) {
        val properties = (event.toProperties().toList() + extra.toList()).toTypedArray()
        track(event.name, *properties)
    }

    private fun increment(property: String, amount: Double) {
        if (BuildConfig.DEBUG) {
            trace("debug increment $property by $amount", type = TraceType.Silent)
            return
        }
        mixpanelAPI.people.increment(property, amount)
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        val resolved = properties.toList().withTokenSymbols(tokenSymbolResolver)

        if (BuildConfig.DEBUG) {
            val propsString = resolved.joinToString { "${it.first} => ${it.second}" }
            trace(
                buildString {
                    append("debug track $name")
                    if (propsString.isNotEmpty()) append(", $propsString")
                },
                type = TraceType.Silent
            )
            return
        }

        val jsonObject = JSONObject()
        resolved.forEach { jsonObject.put(it.first, it.second) }
        mixpanelAPI.track(name, jsonObject)
    }

    private fun Throwable?.asProperty(): Array<Pair<String, String>> =
        this?.let { arrayOf("Error" to it.message.orEmpty()) } ?: emptyArray()
    // endregion
}

/** Mint-carrying property → the symbol property that accompanies it. */
private val MINT_PROPERTIES = mapOf(
    "Mint" to "Token Symbol",
    "Payment Mint" to "Payment Token Symbol",
)

/**
 * Converts [this] event's properties to the values Mixpanel's JSON takes, with a ticker
 * added beside every mint the [resolver] knows (see [withTokenSymbols]).
 */
internal fun com.flipcash.analytics.AnalyticsEvent.toMixpanelProperties(
    resolver: TokenSymbolResolver,
): Map<String, Any> = buildMap {
    properties.forEach { (key, value) ->
        put(
            key,
            when (value) {
                is PropertyValue.Text -> value.value
                is PropertyValue.Number -> value.value
                is PropertyValue.Flag -> value.value
            }
        )
    }
    MINT_PROPERTIES.forEach { (mintKey, symbolKey) ->
        if (symbolKey in this) return@forEach
        val mint = this[mintKey] as? String ?: return@forEach
        resolver.symbolFor(mint)?.let { put(symbolKey, it) }
    }
}

/**
 * Returns [properties] with a ticker added beside every mint the [resolver] knows.
 *
 * An unresolvable mint adds nothing — the property must be absent rather than
 * empty, so a failed cache lookup is distinguishable from a token with no symbol.
 */
internal fun List<Pair<String, String>>.withTokenSymbols(
    resolver: TokenSymbolResolver,
): List<Pair<String, String>> {
    val present = map { it.first }.toSet()
    val symbols = mapNotNull { (key, value) ->
        val symbolKey = MINT_PROPERTIES[key] ?: return@mapNotNull null
        if (symbolKey in present) return@mapNotNull null
        resolver.symbolFor(value)?.let { symbolKey to it }
    }
    return this + symbols
}
