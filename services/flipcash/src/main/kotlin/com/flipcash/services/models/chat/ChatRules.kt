package com.flipcash.services.models.chat

import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.PublicKey

/**
 * Requirements a user must satisfy to participate in a chat. Only supported for group chats;
 * absent entirely means the chat has no participation requirements.
 *
 * [listener] and [speaker] are independently optional: [listener] gates reading and joining,
 * [speaker] gates sending messages. All rules within a class must be satisfied, and speaker
 * rules apply in addition to listener rules — a user must be able to listen before they can
 * speak.
 */
data class ChatRules(
    val listener: List<ChatRuleRequirement>,
    val speaker: List<ChatRuleRequirement>,
)

/**
 * A single requirement gating participation in a chat.
 *
 * The proto models a listener requirement and a speaker requirement as two separate messages
 * (`ListenerRules`, `SpeakerRules`) that share the exact same `kind` oneof shape — a minimum
 * balance or staff membership. Nothing distinguishes one from the other beyond which list it
 * sits in, so this collapses both into one domain type used by [ChatRules.listener] and
 * [ChatRules.speaker] alike.
 */
sealed interface ChatRuleRequirement {
    /** Requires holding at least [amount], denominated in fiat, in one of [mints] (all mints when empty). */
    data class MinimumBalance(
        val amount: Fiat,
        val mints: List<PublicKey>,
    ) : ChatRuleRequirement

    /** Requires Flipcash staff membership, as indicated by `UserFlags.is_staff`. */
    data object Staff : ChatRuleRequirement
}
