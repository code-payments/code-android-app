package com.flipcash.services

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app is in the foreground. Work whose deadline should count foreground time only
 * (chat media finalization) waits on [awaitForeground] before each step, so a backgrounded app
 * neither burns its budget nor polls from a process the OS may be about to freeze.
 */
fun interface ForegroundGate {
    /** Returns immediately when the app is foregrounded, otherwise suspends until it is. */
    suspend fun awaitForeground()
}

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
