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
    private var activeKey: Any? = null

    protected abstract suspend fun doUpdate()

    /**
     * Starts the polling loop, or leaves an equivalent one already running alone.
     *
     * The idempotence matters because callers arrive from lifecycle edges that can fire twice in
     * quick succession — `SessionController.onAppInForeground` is invoked both by the transition
     * into `AuthState.Ready` and by `ON_RESUME`, and at login those land together. Restarting
     * unconditionally cancelled the in-flight [doUpdate] and re-served [startIn], so the first
     * fetch of a fresh session was pushed further out by the very event that asked for it.
     *
     * Backgrounding and logout still reset the loop, through [stop].
     */
    fun poll(
        key: Any? = null,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration = 0.seconds,
    ) {
        if (job?.isActive == true && activeKey == key) return

        stop()
        activeKey = key
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
        activeKey = null
    }
}
