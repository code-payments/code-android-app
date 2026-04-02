package com.flipcash.app.updates.internal

import android.content.Context
import androidx.activity.ComponentActivity
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.UpdateInfo
import com.flipcash.app.userflags.Field
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.getcode.utils.trace
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

@ActivityScoped
class GooglePlayAppUpdateController @Inject constructor(
    @field:ActivityContext
    private val context: Context,
    private val versionInfo: VersionInfo,
    private val userFlags: UserFlagsCoordinator,
) : AppUpdateController {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    override val availableUpdate: StateFlow<UpdateInfo?>
        get() = _availableUpdate.asStateFlow()

    override suspend fun checkForUpdate() {
        trace(
            tag = "App Updates",
            message = "Checking for available updates"
        )

        val minimumVersionFromServer = userFlags.resolvedFlags.value.minimumVersion.effectiveValue ?: Int.MIN_VALUE

        if (versionInfo.versionCode >= minimumVersionFromServer) {
            trace(
                tag = "App Updates",
                message = "Forced update is not required: ${versionInfo.versionCode} >= $minimumVersionFromServer",
                metadata = {
                    "current version" to versionInfo.versionCode
                    "minimum version" to minimumVersionFromServer
                }
            )
            return
        }

        trace(
            tag = "App Updates",
            message = "Checking for available updates - ${versionInfo.versionCode} < $minimumVersionFromServer",
        )

        val task = appUpdateManager.appUpdateInfo

        task.addOnSuccessListener { update ->
            trace(
                tag = "App Updates",
                message = "Update info retrieved successfully",
            )

            // allow minimum version override to be visualized
            val availability = when (val override = userFlags.resolvedFlags.value.minimumVersion.override) {
                is FieldOverride.Value -> {
                    if ((override.value ?: Int.MIN_VALUE) > versionInfo.versionCode) {
                        UpdateAvailability.UPDATE_AVAILABLE
                    } else {
                        UpdateAvailability.UPDATE_NOT_AVAILABLE
                    }
                }
                FieldOverride.None -> update.updateAvailability()
            }

            val availableUpdate = UpdateInfo(
                updateAvailability = availability,
                updatePriority = update.updatePriority(),
                clientVersionStalenessDays = update.clientVersionStalenessDays(),
                bytesDownloaded = update.bytesDownloaded(),
                totalBytesToDownload = update.totalBytesToDownload(),
                installStatus = update.installStatus(),
                availableVersionCode = update.availableVersionCode(),
                rawAppUpdateInfo = update
            )

            _availableUpdate.update { availableUpdate }

            if (availableUpdate.availableVersionCode > versionInfo.versionCode) {
                trace(
                    tag = "App Updates",
                    message = "New update available",
                    metadata = {
                        "current version" to versionInfo.versionCode
                        "available version" to availableUpdate.availableVersionCode
                        "update priority" to update.updatePriority()
                        "bytes downloaded" to update.bytesDownloaded()
                        "total bytes to download" to update.totalBytesToDownload()
                    }
                )
            } else {
                trace(
                    tag = "App Updates",
                    message = "No new update available: ${versionInfo.versionCode} - ${availableUpdate.availableVersionCode}"
                )
            }

            if (update.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // in-app update is already in progress, resume it
                scope.launch {
                    startUpdate()
                }
            }
        }

        task.addOnFailureListener {
            _availableUpdate.update { null }
            trace(
                tag = "App Updates",
                message = "Failed to check for available updates",
                error = it,
            )
        }
    }

    override suspend fun startUpdate(): Result<Unit> = suspendCancellableCoroutine { cont ->
        val info = _availableUpdate.value?.rawAppUpdateInfo as? AppUpdateInfo
        if (info == null) {
            cont.resume(Result.failure(NoUpdateAvailableException()))
            return@suspendCancellableCoroutine
        }

        if (info.availableVersionCode() <= versionInfo.versionCode) {
            cont.resume(Result.failure(NoUpdateAvailableException()))
            return@suspendCancellableCoroutine
        }

        if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            cont.resume(Result.failure(IllegalStateException("Update type is not allowed")))
            return@suspendCancellableCoroutine
        }

        val task = appUpdateManager.startUpdateFlow(
            info,
            context as ComponentActivity,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        )

        task.addOnSuccessListener {
            trace(
                tag = "App Updates",
                message = "Update flow started successfully"
            )
            if (cont.isActive) cont.resume(Result.success(Unit))
        }

        task.addOnCanceledListener {
            trace(
                tag = "App Updates",
                message = "Update flow was canceled"
            )
            if (cont.isActive) cont.resume(Result.failure(Exception("Update flow was canceled.")))
        }

        task.addOnFailureListener {
            trace(
                tag = "App Updates",
                message = "Failed to start update flow",
                error = it,
            )
            if (cont.isActive) cont.resume(Result.failure(it))
        }
    }

    override suspend fun reset() {
        // only allow reset if staff and minimum version has been overridden
        if (userFlags.resolvedFlags.value.isStaff.effectiveValue) {
            if (userFlags.resolvedFlags.value.minimumVersion.isOverridden) {
                _availableUpdate.update { null }
                userFlags.clear(Field.MinimumVersion)
            }
        }
    }
}

class NoUpdateAvailableException: IllegalStateException("No update available")