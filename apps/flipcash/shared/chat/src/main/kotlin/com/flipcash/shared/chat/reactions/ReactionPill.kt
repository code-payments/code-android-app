package com.flipcash.shared.chat.reactions

import kotlin.time.Instant

/**
 * One emoji's displayed reaction on a message, with [count] and [selfReacted] already resolved
 * from the pending tap over the confirmed server state.
 */
data class ReactionPill(
    val emoji: String,
    val count: Long,
    val selfReacted: Boolean,
    /** True while the user's last tap on this emoji has not been reconciled with the server. */
    val pending: Boolean,
    val boost: ReactionBoost? = null,
    /** True when the user paid toward [boost]; their reaction then can't be removed. */
    val selfBoosted: Boolean = false,
)

/** The paid boost on a pill: [total] is the emoji's USD boost in whole cents. */
data class ReactionBoost(val total: Long)

/**
 * [cents] as US dollars with two decimals and grouped thousands ("$1,250.00"). Always USD and
 * never localised: the boost is a USD figure on the wire, and the pill shows it as one.
 */
fun formatBoostCents(cents: Long): String {
    val dollars = cents / 100
    val remainder = cents % 100
    return "$" + "%,d".format(java.util.Locale.US, dollars) + "." + remainder.toString().padStart(2, '0')
}

/** An add or remove the caller must send to the server for one emoji. */
data class ReactionCall(val op: Op, val emoji: String) {
    enum class Op { ADD, REMOVE }
}

/** Why an add or remove call failed, as far as the reaction merge needs to know. */
enum class ReactionFailure {
    NETWORK,
    DENIED,
    MESSAGE_NOT_FOUND,
    CANNOT_REACT,
    TOO_MANY_REACTION_TYPES,
    CANNOT_REMOVE_BOOSTED;

    /** The error to show the user for this failure, or null when the rollback is silent. */
    val userError: ReactionError?
        get() = when (this) {
            NETWORK, DENIED -> ReactionError.REACTION_FAILED
            TOO_MANY_REACTION_TYPES -> ReactionError.TOO_MANY_REACTION_TYPES
            // The reaction stands, and the rollback shows it again: nothing for the user to fix.
            MESSAGE_NOT_FOUND, CANNOT_REACT, CANNOT_REMOVE_BOOSTED -> null
        }
}

/** A reaction failure the user is told about. */
enum class ReactionError {
    REACTION_FAILED,
    TOO_MANY_REACTION_TYPES,
}

/** The outcome of an add or remove call. */
sealed class ReactionResult {
    data class Ok(
        val count: Long,
        val selfReacted: Boolean,
        val version: Long,
        val selfReactedAt: Instant?,
    ) : ReactionResult()

    data class Failed(val failure: ReactionFailure) : ReactionResult()
}

/** An emoji the user's displayed state has them reacting with, and when they reacted. */
data class SelfReaction(val emoji: String, val reactedAt: Instant)
