package com.flipcash.app.updates

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Where the user is, as far as the update toasts care. */
data class UpdateToastScene(
    /** On the Chats or Wallet home, with the bar showing and nothing covering the app. */
    val onTabHome: Boolean,
    /** Settings is the top route. It carries its own version card, so no toast shows over it. */
    val onSettings: Boolean,
)

/** Whether the one-time toast's timer may run. */
val UpdateToastScene.allowsOneTimeToast: Boolean get() = onTabHome && !onSettings

/** Whether the indefinite "Update ready" toast may be up. */
fun allowsReadyToast(downloaded: Boolean, covered: Boolean, onSettings: Boolean): Boolean =
    downloaded && !covered && !onSettings

/**
 * Waits out the settle or dwell time, then calls [show]. Cancelled by collectLatest when the scene
 * or toast changes first, so a toast suppressed mid-wait is never shown and so never marked seen.
 */
suspend fun awaitOneTimeToast(
    toast: OptionalUpdate?,
    scene: UpdateToastScene,
    cameBack: Boolean,
    show: suspend (OptionalUpdate) -> Unit,
) {
    if (toast == null || !scene.allowsOneTimeToast) return
    delay(if (cameBack) UpdateToastTiming.SETTLE_MILLIS else UpdateToastTiming.DWELL_MILLIS)
    show(toast)
}

fun readyToastAllowed(
    downloaded: Flow<Boolean>,
    covered: Flow<Boolean>,
    onSettings: Flow<Boolean>,
): Flow<Boolean> = combine(downloaded, covered, onSettings, ::allowsReadyToast)
