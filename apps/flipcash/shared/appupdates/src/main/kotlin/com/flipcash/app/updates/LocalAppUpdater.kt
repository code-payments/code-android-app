package com.flipcash.app.updates

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalAppUpdater = compositionLocalOf<AppUpdateController> { StubAppUpdateController() }

private class StubAppUpdateController: AppUpdateController {
    override val availableUpdate: StateFlow<UpdateInfo?> = MutableStateFlow(null)

    override suspend fun checkForUpdate() = Unit

    override suspend fun startUpdate(): Result<Unit> {
        return Result.failure(Throwable("This is a stub implementation"))
    }
}