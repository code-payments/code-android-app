package com.flipcash.app.core.updater

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

abstract class NetworkUpdater {
    private var job: Job? = null

    protected abstract suspend fun doUpdate()

    fun poll(
        key: Any? = null,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration = 0.seconds,
    ) {
        stop()
        job = scope.launch {
            delay(startIn)
            while (isActive) {
                doUpdate()
                delay(frequency)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
