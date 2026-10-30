package com.flipcash.app.core.lifecycle

import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * The process-wide lifecycle. On Android it is [androidx.lifecycle.ProcessLifecycleOwner]; on
 * desktop, where `lifecycle-process` has no artifact, it is RESUMED for as long as the app runs.
 */
expect object AppProcessLifecycle {
    val owner: LifecycleOwner

    /**
     * Adds [observer] to [owner]'s lifecycle. Safe to call from any thread: on Android the add is
     * posted to the main thread, which `addObserver` requires.
     */
    fun addObserver(observer: LifecycleObserver)
}
