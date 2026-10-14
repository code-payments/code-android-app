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
    /** A required update: non-null blocks the app (UpdateRequiredBlockingView, no camera). */
    val availableUpdate: StateFlow<UpdateInfo?>

    /**
     * A newer store build the user can take when they like, or null. Never set on debug or
     * internal builds, and never read by the forced-update UI.
     */
    val optionalUpdate: StateFlow<OptionalUpdate?>

    /** The optional update when the one-time toast should show for it, else null. */
    val updateToast: StateFlow<OptionalUpdate?>

    /** The flexible download has finished and [completeUpdate] will install it. */
    val updateDownloaded: StateFlow<Boolean>

    /** Fraction (0..1) of the flexible download done while it runs, else null. */
    val updateProgress: StateFlow<Float?>

    suspend fun checkForUpdate()

    /** Asks Play for an optional update. Leaves [availableUpdate] alone. */
    suspend fun checkForOptionalUpdate()

    suspend fun startUpdate(): Result<Unit>

    /** Starts Play's flexible download of [optionalUpdate], which runs while the app stays usable. */
    suspend fun startOptionalUpdate(): Result<Unit>

    /** Installs a downloaded flexible update, restarting the app. */
    fun completeUpdate()

    /** Records that the toast has shown for [update], so it never shows for that version again. */
    suspend fun markUpdateToastShown(update: OptionalUpdate)

    suspend fun reset()
}