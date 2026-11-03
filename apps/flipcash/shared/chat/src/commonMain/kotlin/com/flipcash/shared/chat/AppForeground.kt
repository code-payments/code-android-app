package com.flipcash.shared.chat

import androidx.lifecycle.DefaultLifecycleObserver

/**
 * Whether the app is in the foreground, and a way to hear when that changes. Chat reads it instead
 * of the process lifecycle so the module does not need Android's `ProcessLifecycleOwner`.
 */
interface AppForeground {
    /** True while the app is at least started. */
    val isForeground: Boolean

    /** Calls [observer]'s `onStart`/`onStop` as the app enters and leaves the foreground. */
    fun observe(observer: DefaultLifecycleObserver)

    /** Never in the foreground and never notifies. The default for a caller built without a host, as in unit tests. */
    object None : AppForeground {
        override val isForeground: Boolean = false
        override fun observe(observer: DefaultLifecycleObserver) = Unit
    }
}
