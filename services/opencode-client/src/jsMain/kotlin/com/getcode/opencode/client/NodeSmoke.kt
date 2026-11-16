package com.getcode.opencode.client

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.transport.NodeGrpcTransport
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.promise
import kotlinx.coroutines.withTimeout
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

/**
 * The one export CI's Node smoke test calls: opens StreamLiveMintData against [host] (a read call)
 * and resolves with a one-line summary of the first batch of verified rates. Not SDK API; step 5's
 * sdk-js module defines that.
 */
@OptIn(DelicateCoroutinesApi::class)
@JsExport
fun smokeFetchRates(host: String): Promise<String> = GlobalScope.promise {
    val transport = NodeGrpcTransport(host)
    try {
        val rates = withTimeout(30.seconds) {
            CurrencyClient(transport).streamLiveMintData(listOf(Mint.usdf))
                .filterIsInstance<LiveMintDataResponse.ExchangeRates>()
                .first()
        }.rates
        check(rates.isNotEmpty()) { "StreamLiveMintData returned no rates" }
        val usd = rates.firstOrNull { it.rate.currency == CurrencyCode.USD }
        "StreamLiveMintData: ${rates.size} verified rates, USD=${usd?.rate?.fx} at ${usd?.timestamp}"
    } finally {
        transport.close()
    }
}
