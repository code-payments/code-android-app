package com.flipcash.app.userflags

import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.minus

/**
 * Whether the signed-in account may claim a handle, and how far off it is if not.
 *
 * The server enforces the same minimum on submit (`SetUsernameError.InsufficientBalance`); this is
 * the local reading of it, so the You tab can show the distance rather than let the user type a
 * handle into a rejection.
 *
 * Kept as arithmetic over [Fiat] rather than the card's presentation type: the caller owns
 * formatting and string resources, this owns the rule.
 *
 * Mirrors iOS `usernameGate(session:minimum:)` in `UsernameGate.swift`.
 */
sealed interface UsernameGate {
    /**
     * A handle the user chose is already claimed — the nudge is spent, and changing it lives in
     * My Account. A handle the server assigned does not count: the user hasn't picked it.
     */
    data object Claimed : UsernameGate

    /** Nothing in the way: either the balance clears the minimum, or there is no minimum. */
    data object Unlocked : UsernameGate

    /**
     * Short of the minimum by [shortfall], which is [fraction] of the way there.
     *
     * [fraction] is in `0f..1f` by construction — this arm is only reached below [minimum].
     */
    data class Locked(
        val minimum: Fiat,
        val shortfall: Fiat,
        val fraction: Float,
    ) : UsernameGate
}

/**
 * @param username the account's handle, null or blank when it has none.
 * @param isAutoAssigned whether [username] was assigned by the server rather than chosen by the
 *   user. An assigned handle still counts as unclaimed, so the nudge keeps showing.
 * @param minimum the balance the account must hold to claim, from the `usernameMinBalance` flag.
 * @param balance the account's total balance, in the same currency as [minimum].
 */
fun usernameGate(
    username: String?,
    isAutoAssigned: Boolean,
    minimum: Fiat,
    balance: Fiat,
): UsernameGate = when {
    !username.isNullOrBlank() && !isAutoAssigned -> UsernameGate.Claimed
    // A zero minimum is no gate at all — which is also what an unresolved flag looks like. Either
    // reading leaves nothing holding the account back, so both fail open.
    !minimum.isPositive -> UsernameGate.Unlocked
    balance.valueGreaterThanOrEqualTo(minimum) -> UsernameGate.Unlocked
    else -> UsernameGate.Locked(
        minimum = minimum,
        shortfall = minimum - balance,
        fraction = (balance.toDouble() / minimum.toDouble()).toFloat().coerceIn(0f, 1f),
    )
}
