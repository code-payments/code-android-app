package com.flipcash.services.analytics

import com.getcode.libs.analytics.AnalyticsService
import com.getcode.libs.analytics.AppAction
import com.getcode.libs.analytics.AppActionSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.services.flipcash.BuildConfig
import com.getcode.utils.TraceType
import com.getcode.utils.trace
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
import com.google.firebase.perf.metrics.Trace
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

interface FlipcashAnalyticsService : AnalyticsService {
    fun transfer(
        event: AnalyticsEvent,
        amount: LocalFiat? = null,
        successful: Boolean = true,
        error: Throwable? = null
    )
    fun transfer(
        event: AnalyticsEvent,
        fiat: Fiat? = null,
        successful: Boolean = true,
        error: Throwable? = null
    )
}

class FlipcashAnalyticsManager @Inject constructor(
    private val mixpanelAPI: MixpanelAPI
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
            metadata = {
                "duration" to duration
            },
            type = TraceType.Process
        )
    }

    override fun unintentionalLogout() = Unit

    override fun action(action: AppAction, source: AppActionSource?) = Unit

    override fun transfer(event: AnalyticsEvent, amount: LocalFiat?, successful: Boolean, error: Throwable?) {
        val properties = event.properties(localizedAmount = amount, successful = successful, error = error)
        track(event.name, *properties.toList().toTypedArray())
    }

    override fun transfer(event: AnalyticsEvent, fiat: Fiat?, successful: Boolean, error: Throwable?) {
        val properties = event.properties(nativeAmount = fiat, successful = successful, error = error)
        track(event.name, *properties.toList().toTypedArray())
    }

    private fun track(name: String, vararg properties: Pair<String, String>) {
        if (BuildConfig.DEBUG) {
            trace(
                "debug track ${name}, ${properties.joinToString { "${it.first} => ${it.second}" }}",
                type = TraceType.Silent
            )
            return
        } //no logging in debug

        val jsonObject = JSONObject()
        properties.forEach { property ->
            jsonObject.put(property.first, property.second)
        }
        mixpanelAPI.track(name, jsonObject)
    }
}

sealed class AnalyticsEvent(val name: String) {
    data object GrabBill : AnalyticsEvent("Grab Bill")
    data object GiveBill : AnalyticsEvent("Give Bill")
    data object Withdrawal : AnalyticsEvent("Withdrawal")
    data class SentCashLink(val clipboard: Boolean? = null, val app: String? = null) : AnalyticsEvent("Sent Cash Link")
    data object ClaimedCashLink : AnalyticsEvent("Receive Cash Link")
}

private fun AnalyticsEvent.properties(
    localizedAmount: LocalFiat? = null,
    nativeAmount: Fiat? = null,
    successful: Boolean,
    error: Throwable?
): Map<String, String> {
    return buildMap {
        if (successful) {
            put("State", "Success")
        } else {
            put("State", "Failure")
        }

        when (val event = this@properties) {
            is AnalyticsEvent.SentCashLink -> {
                if (event.clipboard == true) {
                    put("Cash Link Choice", "Copied to clipboard")
                } else if (event.app != null) {
                    put("Cash Link Choice", "Shared to app")
                    put("App", event.app)
                }
            }
            AnalyticsEvent.Withdrawal,
            AnalyticsEvent.ClaimedCashLink,
            AnalyticsEvent.GiveBill,
            AnalyticsEvent.GrabBill -> Unit
        }

        if (localizedAmount != null) {
            putAll(localizedAmount.asProperties())
        } else if (nativeAmount != null) {
            putAll(nativeAmount.asProperties())
        }

        if (error != null) {
            put("Error", error.message.orEmpty())
        }
    }
}

private fun LocalFiat.asProperties(): Map<String, String> {
    return buildMap {
        putAll(usdc.asProperties())
        "Fiat" to converted.doubleValue.toString()
        "Exchange Rate" to rate.fx.toString()
        "Currency" to rate.currency.name
    }
}

private fun Fiat.asProperties(): Map<String, String> {
    return buildMap {
        "USDC" to doubleValue.toString()
        "Quarks" to quarks.toDouble().toString()
    }
}