package com.getcode.opencode.compose

import android.content.Context
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Rate
import com.getcode.services.opencode.R

/** An [ExchangeStub] that also resolves flag drawables through [context]. */
fun ExchangeStub(
    providedRates: Map<CurrencyCode, Rate> = emptyMap(),
    context: Context,
): Exchange = AndroidExchangeStub(providedRates, context)

private class AndroidExchangeStub(
    providedRates: Map<CurrencyCode, Rate>,
    private val context: Context,
) : ExchangeStub(providedRates) {

    override fun getFlagByCurrency(currencyCode: String?): Int? {
        currencyCode ?: return null
        if (currencyCode == "XAF") return R.drawable.ic_currency_xaf
        if (currencyCode == "XOF") return R.drawable.ic_currency_xof

        return CurrencyCode.tryValueOf(currencyCode)?.let { currency ->
            currency.getRegion()?.name
        }?.let { regionName ->
            getFlag(regionName)
        }
    }

    override fun getFlag(countryCode: String): Int? {
        if (countryCode.isEmpty()) return null
        val resourceName = "ic_flag_${countryCode.lowercase()}"
        return context.resources.getIdentifier(
            resourceName,
            "drawable",
            context.packageName

        ).let { if (it == 0) null else it }
    }
}
