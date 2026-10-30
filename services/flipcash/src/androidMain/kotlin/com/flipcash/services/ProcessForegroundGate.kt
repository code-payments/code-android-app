package com.flipcash.services

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [ForegroundGate] backed by [ProcessLifecycleOwner]: foreground means at least STARTED. */
@Singleton
internal class ProcessForegroundGate @Inject constructor() : ForegroundGate {
    override suspend fun awaitForeground() {
        // The process lifecycle registry only accepts observers on the main thread.
        withContext(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.currentStateFlow
                .first { it.isAtLeast(Lifecycle.State.STARTED) }
        }
    }
}
