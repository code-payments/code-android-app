package com.flipcash.app.updates.internal

import android.content.Context
import androidx.activity.ComponentActivity
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.OptionalUpdate
import com.flipcash.app.updates.isToastEligible
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
import com.flipcash.libs.coroutines.DispatcherProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@ActivityScoped
class GooglePlayAppUpdateController internal constructor(
    @field:ActivityContext
    private val context: Context,
    private val versionInfo: VersionInfo,
    private val userFlags: UserFlagsCoordinator,
    private val dispatchers: DispatcherProvider,
    private val flexibleUpdates: FlexibleUpdates,
    private val toastSeenStore: UpdateToastSeenStore,
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context),
) : AppUpdateController {
    private val scope = CoroutineScope(dispatchers.IO + SupervisorJob())

    private val _availableUpdate = MutableStateFlow<UpdateInfo?>(null)
    override val availableUpdate: StateFlow<UpdateInfo?>
        get() = _availableUpdate.asStateFlow()

    override val optionalUpdate: StateFlow<OptionalUpdate?>
        get() = flexibleUpdates.update

    override val updateToast: StateFlow<OptionalUpdate?> =
        combine(flexibleUpdates.update, toastSeenStore.lastShownVersionCode) { update, lastShown ->
            update?.takeIf { it.isToastEligible(lastShown) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // The resume check reads the minimum version cached from the last fetch, and the fetch that
        // runs on the same resume lands after it. Checking again when the minimum moves is what
        // lets a minimum raised while the app sat in the background block it on return. The first
        // value is left to the resume check.
        scope.launch {
            userFlags.resolvedFlags
                .map { it.minimumVersion.effectiveValue }
                .distinctUntilChanged()
                .drop(1)
                .collect { checkForUpdate() }
        }
    }

    override val updateDownloaded: StateFlow<Boolean>
        get() = flexibleUpdates.downloaded

    override val updateProgress: StateFlow<Float?>
        get() = flexibleUpdates.progress

    override suspend fun checkForOptionalUpdate() = flexibleUpdates.check()

    override suspend fun startOptionalUpdate(): Result<Unit> =
        flexibleUpdates.start(context as ComponentActivity)

    override fun completeUpdate() = flexibleUpdates.complete()

    override suspend fun markUpdateToastShown(update: OptionalUpdate) =
        toastSeenStore.markShown(update.versionCode)

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

        // Play reports IMMEDIATE as not allowed while one is already under way; resuming it is.
        val resuming = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        if (!resuming && !info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
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