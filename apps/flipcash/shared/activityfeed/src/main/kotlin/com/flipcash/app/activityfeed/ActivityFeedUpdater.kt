package com.flipcash.app.activityfeed

import com.flipcash.app.core.updater.NetworkUpdater
import javax.inject.Inject

class ActivityFeedUpdater @Inject constructor(
    private val coordinator: ActivityFeedCoordinator,
): NetworkUpdater() {
    override suspend fun doUpdate() {
        coordinator.fetchSinceLatest(count = 50)
    }
}
