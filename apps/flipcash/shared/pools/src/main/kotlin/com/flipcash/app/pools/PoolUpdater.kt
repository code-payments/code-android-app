package com.flipcash.app.pools

import com.flipcash.app.core.cache.CachePolicy
import com.flipcash.app.core.updater.NetworkUpdater
import com.getcode.opencode.model.core.ID
import com.getcode.utils.base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class PoolUpdater @Inject constructor(
    private val coordinator: PoolsCoordinator
): NetworkUpdater() {
    override fun poll(
        key: Any?,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration
    ) {
        stop()
        val poolId = key as? ID ?: return

        updater = fixedRateTimer(
            name = "update pool => ${poolId.base58}",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
               coordinator.updatePool(poolId)
            }
        }
    }
}