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

    /** Nobody may take the action (`chat.v1.Never`). Only the server sends it, and only as a speaker rule. */
    data object Never : ChatRuleRequirement

    /**
     * Only the chat's creator (`ChatMetadata.creator`, `chat.v1.Metadata.creator`) may take the
     * action (`chat.v1.CreatorRequirement`). Speaker-only: `ListenerRules` has no creator arm. The
     * requirement carries no id; it is met when the viewer's user id equals the chat's creator.
     */
    data object Creator : ChatRuleRequirement

    /**
     * A speaker rule this build cannot decode: the oneof was unset, or carries a case added by a
     * newer contract. Decoding it as a requirement rather than dropping it keeps the chat closed;
     * dropping it would read as "no requirement" and let everyone post. Nobody satisfies it, staff
     * included. Client-only: never encoded back to the wire, and never a listener rule.
     */
    data object UnsupportedSpeakerRule : ChatRuleRequirement
}

/**
 * Whether leaving this requirement unmet also withholds reactions, not only posting.
 *
 * [ChatRuleRequirement.Creator] and [ChatRuleRequirement.UnsupportedSpeakerRule] gate posting (the
 * composer and Reply) and nothing else: any member can still react, copy and report. The rest also
 * withhold reactions.
 *
 * Collecting a cash link from a chat follows this too: a member blocked by a rule that withholds
 * reactions is refused the claim (the messenger's `ChatViewModel.State.cashCardTap`).
 */
val ChatRuleRequirement.blocksReactions: Boolean
    get() = when (this) {
        is ChatRuleRequirement.MinimumBalance,
        ChatRuleRequirement.Staff,
        ChatRuleRequirement.Never -> true
        ChatRuleRequirement.Creator,
        ChatRuleRequirement.UnsupportedSpeakerRule -> false
    }
