package com.flipcash.app.pools

import com.flipcash.app.core.updater.NetworkUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class PoolsUpdater @Inject constructor(
    private val coordinator: PoolsCoordinator
): NetworkUpdater() {
    override fun poll(
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration,
    ) {
        stop()
        updater = fixedRateTimer(
            name = "update pools",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                coordinator.updatePools()
            }

            scope.launch {
                coordinator.fetchSinceLatest(count = 50)
            }
        }
    }
}