package com.flipcash.app.tokens

import com.flipcash.app.core.updater.NetworkUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class TokenUpdater @Inject constructor(
    private val coordinator: TokenCoordinator,
) : NetworkUpdater() {
    override fun poll(
        key: Any?,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration,
    ) {
        stop()
        updater = fixedRateTimer(
            name = "update tokens",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                coordinator.update()
            }
        }
    }
}