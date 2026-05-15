package com.flipcash.app.tokens

import com.flipcash.app.core.updater.NetworkUpdater
import javax.inject.Inject

class TokenUpdater @Inject constructor(
    private val coordinator: TokenCoordinator,
) : NetworkUpdater() {
    override suspend fun doUpdate() {
        coordinator.update()
    }
}
