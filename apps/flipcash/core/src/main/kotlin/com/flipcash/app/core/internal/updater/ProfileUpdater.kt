package com.flipcash.app.core.internal.updater

import com.flipcash.app.core.updater.NetworkUpdater
import com.flipcash.services.controllers.ProfileController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration

class ProfileUpdater @Inject constructor(
    private val profileController: ProfileController,
) : NetworkUpdater() {
    override fun poll(
        key: Any?,
        scope: CoroutineScope,
        frequency: Duration,
        startIn: Duration
    ) {
        stop()
        updater = fixedRateTimer(
            name = "update profile",
            initialDelay = startIn.inWholeMilliseconds,
            period = frequency.inWholeMilliseconds
        ) {
            scope.launch {
                profileController.updateUserProfile()
            }
        }
    }
}