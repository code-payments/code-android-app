package com.flipcash.app.core.internal.updater

import com.getcode.opencode.controllers.BalanceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class BalanceUpdater @Inject constructor(
    private val balanceController: BalanceController,
) {
    private var updater: Timer? = null

    fun poll(
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration = 0.seconds,
    ) {
        updater = fixedRateTimer(
            name = "update balance",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                balanceController.fetchBalance()
            }
        }
    }

    fun stop() {
        updater?.cancel()
        updater = null
    }
}