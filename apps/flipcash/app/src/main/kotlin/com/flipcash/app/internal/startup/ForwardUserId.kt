package com.flipcash.app.internal.startup

import com.bugsnag.android.Bugsnag
import com.getcode.utils.TraceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Forwards every [TraceManager.userId] change to [setUser] on [dispatcher], never on the thread
 * that changed it.
 *
 * `Bugsnag.setUser` takes the NDK plugin's state lock, and Bugsnag's own worker holds that lock in
 * `NativeBridge.synchronizeState` while it waits on `DeviceDataCollector.calculateFreeDisk`. Account
 * creation sets the user id on the main thread, so calling Bugsnag there froze the UI until the
 * worker let go — the ANR in Bugsnag `6acec70adbd69358a81f2824`.
 *
 * [dispatcher] runs one change at a time, in order, so a sign-out followed by a sign-in leaves
 * Bugsnag with the signed-in id.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun forwardUserIdOffThread(
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    setUser: (String?) -> Unit = { id -> Bugsnag.setUser(id, null, null) },
) {
    TraceManager.setOnUserIdChanged { id ->
        launchBestEffort(tag = "bugsnag-user", dispatcher = dispatcher) { setUser(id) }
    }
}
