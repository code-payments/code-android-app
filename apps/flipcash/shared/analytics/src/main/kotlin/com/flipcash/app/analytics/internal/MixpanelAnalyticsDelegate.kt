package com.flipcash.app.analytics.internal

import com.flipcash.analytics.AnalyticsEvent
import com.flipcash.analytics.PeopleCounter
import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.analytics.PropertyValue
import com.flipcash.app.analytics.TokenSymbolResolver
import com.getcode.services.flipcash.BuildConfig
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import javax.inject.Inject

internal class MixpanelAnalyticsDelegate @Inject constructor(
    private val mixpanelAPI: MixpanelAPI,
    private val tokenSymbolResolver: TokenSymbolResolver,
) : FlipcashAnalytics {

    override fun track(event: AnalyticsEvent) {
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

    // region Internal

    private fun increment(property: String, amount: Double) {
        if (BuildConfig.DEBUG) {
            trace("debug increment $property by $amount", type = TraceType.Silent)
            return
        }
        mixpanelAPI.people.increment(property, amount)
    }

    // endregion
}

/** Mint-carrying property → the symbol property that accompanies it. */
private val MINT_PROPERTIES = mapOf(
    "Mint" to "Token Symbol",
    "Payment Mint" to "Payment Token Symbol",
)

/**
 * Converts [this] event's properties to the values Mixpanel's JSON takes, with a ticker
 * added beside every mint the [resolver] knows.
 */
internal fun AnalyticsEvent.toMixpanelProperties(
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
