package com.getcode.opencode.internal.platform

/**
 * Calls [onStart] and [onStop] as the app process enters and leaves the foreground.
 *
 * Registration replays the current state, so a process that is already started calls [onStart]
 * once registered. With [immediateOnMain], a caller already on the main thread registers in place;
 * otherwise registration is always deferred, so neither callback runs during the caller's own
 * construction.
 */
internal expect fun observeProcessLifecycle(
    immediateOnMain: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit = {},
)
