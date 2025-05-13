package com.flipcash.app.core.internal.updater

import com.flipcash.app.core.updater.NetworkUpdater
import com.getcode.opencode.exchange.Exchange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ExchangeUpdater @Inject constructor(
    private val exchange: Exchange,
): NetworkUpdater() {
    override fun poll(
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration,
    ) {
        updater = fixedRateTimer(
            name = "update exchange rates",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                exchange.fetchRatesIfNeeded()
            }
        }
    }
}