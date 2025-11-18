package com.flipcash.app.updates.internal

import android.content.Context
import androidx.activity.ComponentActivity
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.UpdateInfo
import com.flipcash.services.user.UserManager
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
    private val userManager: UserManager,
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

        val minimumVersionFromServer = userManager.userFlags?.minimumVersion ?: Int.MIN_VALUE

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

        val task = appUpdateManager.appUpdateInfo

        task.addOnSuccessListener { update ->
            trace(
                tag = "App Updates",
                message = "Update info retrieved successfully",
            )

            val availableUpdate = UpdateInfo(
                updateAvailability = update.updateAvailability(),
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
            cont.resumeWith(Result.failure(IllegalStateException("No update available")))
            return@suspendCancellableCoroutine
        }

        if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
            cont.resumeWith(Result.failure(IllegalStateException("Update type is not allowed")))
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
            if (cont.isActive) cont.resumeWith(Result.failure(Exception("Update flow was canceled.")))
        }

        task.addOnFailureListener {
            trace(
                tag = "App Updates",
                message = "Failed to start update flow",
                error = it,
            )
            if (cont.isActive) cont.resumeWith(Result.failure(it))
        }
    }
}