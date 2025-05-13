package com.flipcash.app.activityfeed

import com.flipcash.app.core.updater.NetworkUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class ActivityFeedUpdater @Inject constructor(
    private val coordinator: ActivityFeedCoordinator,
): NetworkUpdater() {
    override fun poll(
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration,
    ) {
        updater = fixedRateTimer(
            name = "update activity feed",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                coordinator.fetchSinceLatest(count = 50)
            }
        }
    }
}