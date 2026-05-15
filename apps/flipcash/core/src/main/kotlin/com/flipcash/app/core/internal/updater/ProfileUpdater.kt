package com.flipcash.app.core.internal.updater

import com.flipcash.app.core.updater.NetworkUpdater
import com.flipcash.services.controllers.ProfileController
import javax.inject.Inject

class ProfileUpdater @Inject constructor(
    private val profileController: ProfileController,
) : NetworkUpdater() {
    override suspend fun doUpdate() {
        profileController.updateUserProfile()
    }
}
