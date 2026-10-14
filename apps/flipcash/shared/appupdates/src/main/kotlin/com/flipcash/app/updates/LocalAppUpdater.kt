package com.flipcash.app.updates

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalAppUpdater = staticCompositionLocalOf<AppUpdateController> { StubAppUpdateController() }

class StubAppUpdateController(
    updateInfo: UpdateInfo? = null,
    optionalUpdate: OptionalUpdate? = null,
): AppUpdateController {
    override val availableUpdate: StateFlow<UpdateInfo?> = MutableStateFlow(updateInfo)

    override val optionalUpdate: StateFlow<OptionalUpdate?> = MutableStateFlow(optionalUpdate)

    override val updateToast: StateFlow<OptionalUpdate?> = MutableStateFlow(null)

    override val updateProgress: StateFlow<Float?> = MutableStateFlow(null)

    override val updateDownloaded: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun checkForUpdate() = Unit

    override suspend fun checkForOptionalUpdate() = Unit

    override suspend fun startOptionalUpdate(): Result<Unit> {
        return Result.failure(Throwable("This is a stub implementation"))
    }

    override fun completeUpdate() = Unit

    override suspend fun markUpdateToastShown(update: OptionalUpdate) = Unit

    override suspend fun startUpdate(): Result<Unit> {
        return Result.failure(Throwable("This is a stub implementation"))
    }

    override suspend fun reset() = Unit
}