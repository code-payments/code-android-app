package com.flipcash.services

/**
 * Whether the app is in the foreground. Work whose deadline should count foreground time only
 * (chat media finalization) waits on [awaitForeground] before each step, so a backgrounded app
 * neither burns its budget nor polls from a process the OS may be about to freeze.
 */
fun interface ForegroundGate {
    /** Returns immediately when the app is foregrounded, otherwise suspends until it is. */
    suspend fun awaitForeground()
}
