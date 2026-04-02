package com.flipcash.app.updates

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalAppUpdater = compositionLocalOf<AppUpdateController> { StubAppUpdateController() }

class StubAppUpdateController(updateInfo: UpdateInfo? = null): AppUpdateController {
    override val availableUpdate: StateFlow<UpdateInfo?> = MutableStateFlow(updateInfo)

    override suspend fun checkForUpdate() = Unit

    override suspend fun startUpdate(): Result<Unit> {
        return Result.failure(Throwable("This is a stub implementation"))
    }

    override suspend fun reset() = Unit
}