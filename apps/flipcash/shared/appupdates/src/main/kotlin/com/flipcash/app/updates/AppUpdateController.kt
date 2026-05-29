package com.flipcash.app.updates

import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.StateFlow

data class UpdateInfo(
    val updateAvailability: Int,
    val updatePriority: Int,
    val clientVersionStalenessDays: Int?,
    val bytesDownloaded: Long,
    val totalBytesToDownload: Long,
    val installStatus: Int,
    val availableVersionCode: Int,
    // Internal - only used by real implementation
    internal val rawAppUpdateInfo: Any? = null
) {
    val isUpdateAvailable: Boolean
        get() = updateAvailability == UpdateAvailability.UPDATE_AVAILABLE



    val isDownloaded: Boolean
        get() = installStatus == InstallStatus.DOWNLOADED
}

interface AppUpdateController {
    val availableUpdate: StateFlow<UpdateInfo?>

    suspend fun checkForUpdate()

    suspend fun startUpdate(): Result<Unit>
    suspend fun reset()
}