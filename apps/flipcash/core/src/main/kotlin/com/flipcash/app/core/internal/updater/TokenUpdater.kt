package com.flipcash.app.core.internal.updater

import com.flipcash.app.core.updater.NetworkUpdater
import com.getcode.opencode.controllers.TokenController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class TokenUpdater @Inject constructor(
    private val tokenController: TokenController,
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
                tokenController.update()
            }
        }
    }
}