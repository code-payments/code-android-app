package com.flipcash.app.updates.internal

import android.app.Activity
import com.flipcash.app.updates.OptionalUpdate
import com.getcode.utils.trace
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play's optional, in-place (FLEXIBLE) update: whether one exists, starting its download, and
 * installing it once downloaded. Kept apart from the forced IMMEDIATE path in
 * [GooglePlayAppUpdateController], which it never touches.
 *
 * @param isStoreBuild false on debug and internal builds, which never offer an optional update.
 * @param isInForeground whether the app is on screen; a download finishing in the background is
 * installed straight away rather than waiting for a Restart tap nobody can see.
 */
internal class FlexibleUpdates(
    private val manager: AppUpdateManager,
    private val currentVersionCode: Int,
    private val isStoreBuild: () -> Boolean,
    private val isInForeground: () -> Boolean,
    /** The marketing version for a Play version code, or null when it isn't known. */
    private val versionNameFor: (versionCode: Int) -> String? = { null },
) {
    private val _update = MutableStateFlow<OptionalUpdate?>(null)
    val update: StateFlow<OptionalUpdate?> = _update.asStateFlow()

    private val _downloaded = MutableStateFlow(false)
    val downloaded: StateFlow<Boolean> = _downloaded.asStateFlow()

    private val _progress = MutableStateFlow<Float?>(null)

    /** How much of the download is done (0..1) while Play is fetching it, else null. */
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    private var info: AppUpdateInfo? = null
    private var listener: InstallStateUpdatedListener? = null

    suspend fun check() {
        if (!isStoreBuild()) {
            info = null
            _update.value = null
            return
        }

        val info = runCatching { manager.appUpdateInfo.await() }
            .onFailure { trace(tag = TAG, message = "Failed to check for an optional update", error = it) }
            .getOrNull()
        this.info = info
        if (info == null) {
            _update.value = null
            return
        }

        // A download that finished while the app was gone never reaches the listener.
        if (info.installStatus() == InstallStatus.DOWNLOADED) _downloaded.value = true

        val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
            info.availableVersionCode() > currentVersionCode
        _update.value = if (available) {
            OptionalUpdate(
                versionCode = info.availableVersionCode(),
                priority = info.updatePriority(),
                stalenessDays = info.clientVersionStalenessDays() ?: 0,
                versionName = versionNameFor(info.availableVersionCode()),
            )
        } else {
            null
        }
    }

    fun start(activity: Activity): Result<Unit> {
        val info = info?.takeIf { _update.value != null }
            ?: return Result.failure(NoUpdateAvailableException())

        if (listener == null) {
            val listener = InstallStateUpdatedListener { state ->
                when (state.installStatus()) {
                    InstallStatus.PENDING,
                    InstallStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        _progress.value = if (total > 0) {
                            (state.bytesDownloaded().toFloat() / total).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                    InstallStatus.DOWNLOADED -> {
                        trace(tag = TAG, message = "Optional update downloaded")
                        _progress.value = null
                        _downloaded.value = true
                        if (!isInForeground()) complete()
                    }
                    InstallStatus.INSTALLED,
                    InstallStatus.FAILED,
                    InstallStatus.CANCELED -> {
                        _progress.value = null
                        unregister()
                    }
                    else -> Unit
                }
            }
            manager.registerListener(listener)
            this.listener = listener
        }

        val task = manager.startUpdateFlow(
            info,
            activity,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
        )
        task.addOnFailureListener {
            trace(tag = TAG, message = "Failed to start the optional update", error = it)
            unregister()
        }
        // The user declining Play's dialog ends the attempt; they can start it again from the bubble.
        task.addOnSuccessListener { result -> if (result != Activity.RESULT_OK) unregister() }
        return Result.success(Unit)
    }

    fun complete() {
        unregister()
        manager.completeUpdate()
    }

    private fun unregister() {
        // A declined or failed attempt has no download to show.
        if (!_downloaded.value) _progress.value = null
        listener?.let(manager::unregisterListener)
        listener = null
    }

    /** Reads a task Play already finished without a trip through the main looper. */
    private suspend fun <T> Task<T>.await(): T {
        if (isComplete) return exception?.let { throw it } ?: result
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                task.exception?.let { cont.resumeWithException(it) } ?: cont.resume(task.result)
            }
        }
    }

    private companion object {
        const val TAG = "App Updates"
    }
}
