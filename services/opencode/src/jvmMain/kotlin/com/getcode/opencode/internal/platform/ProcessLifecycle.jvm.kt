package com.getcode.opencode.internal.platform

import java.util.concurrent.Executors

private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "process-lifecycle").apply { isDaemon = true } }

/** A desktop process is in the foreground for as long as it runs, so only [onStart] is ever called. */
internal actual fun observeProcessLifecycle(
    immediateOnMain: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    dispatcher.execute(onStart)
}
