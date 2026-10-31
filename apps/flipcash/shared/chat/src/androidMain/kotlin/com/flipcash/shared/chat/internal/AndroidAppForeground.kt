package com.flipcash.shared.chat.internal

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.shared.chat.AppForeground
import javax.inject.Inject
import javax.inject.Singleton

/** [AppForeground] over the process lifecycle. */
@Singleton
internal class AndroidAppForeground @Inject constructor() : AppForeground {

    override val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun observe(observer: DefaultLifecycleObserver) {
        // Posted, not called directly: addObserver is main-thread-only, and callers are built off
        // the main thread so their construction stays off the startup path.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        }
    }
}
