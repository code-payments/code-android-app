package com.getcode.opencode.internal.platform

import java.util.concurrent.Executors

private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "process-lifecycle").apply { isDaemon = true } }

/**
 * Set to `false` to keep [onStart] from running, so tests can build the graph without the
 * exchange opening its rate stream to the backend.
 */
private const val AUTO_START_PROPERTY = "opencode.processLifecycle.autoStart"

/** A desktop process is in the foreground for as long as it runs, so only [onStart] is ever called. */
internal actual fun observeProcessLifecycle(
    immediateOnMain: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    if (System.getProperty(AUTO_START_PROPERTY) == "false") return
    dispatcher.execute(onStart)
}
