package com.flipcash.app.menu.internal.components

/**
 * How close the account is to being allowed a `@handle`. Null in the state once a handle the user
 * picked exists; otherwise it decides whether the claim link opens the entry screen or states the
 * minimum balance.
 */
internal sealed interface UsernameProgress {
    /**
     * @param fraction how much of the minimum the balance covers, `0f..1f`.
     * @param remaining the shortfall, already formatted for display (e.g. `$12.50 USD`).
     */
    data class Locked(val fraction: Float, val remaining: String) : UsernameProgress

    data object Unlocked : UsernameProgress
}
