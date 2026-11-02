package com.flipcash.shared.chat

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.GroupBalanceRole
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate

/** The group's listener minimum balance, the rule behind the Join row. */
val ChatRules.listenerMinimum: ChatRuleRequirement.MinimumBalance?
    get() = listener.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()

/**
 * The group's own speaker minimum balance, the rule behind the Chat row, or null when speaking
 * needs nothing beyond joining.
 */
val ChatRules.speakerMinimum: ChatRuleRequirement.MinimumBalance?
    get() = speaker.filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()

/**
 * Whether `EditChatRequest.rules` would accept a change to these rules: a public group created
 * with a listener minimum. A private group is DENIED and any other group INVALID_RULES.
 *
 * A rule this build cannot decode also rules it out. The edit sends the whole set back, and
 * [ChatRuleRequirement.UnsupportedSpeakerRule] has no wire form, so it could only be dropped,
 * which would open the chat it was keeping closed.
 */
fun isBalanceEditable(rules: ChatRules?, isPrivate: Boolean): Boolean {
    if (isPrivate || rules == null) return false
    if (rules.listenerMinimum == null) return false
    return ChatRuleRequirement.UnsupportedSpeakerRule !in rules.listener + rules.speaker
}

/**
 * These rules with [role]'s minimum balance set to [amount], and everything else as it was.
 *
 * The listener rule keeps its mints. A speaker rule always takes the listener's mints, the
 * server's condition for accepting one, and replaces the group's own speaker minimum in place, or
 * is appended when there is none. [amount] is expected in the listener rule's currency.
 */
fun ChatRules.withMinimum(role: GroupBalanceRole, amount: Fiat): ChatRules {
    val listenerRule = listenerMinimum ?: return this
    return when (role) {
        GroupBalanceRole.Join -> copy(
            listener = listener.map { if (it === listenerRule) listenerRule.copy(amount = amount) else it },
        )

        GroupBalanceRole.Chat -> {
            val replacement = ChatRuleRequirement.MinimumBalance(amount = amount, mints = listenerRule.mints)
            val existing = speakerMinimum
            copy(
                speaker = if (existing == null) speaker + replacement
                else speaker.map { if (it === existing) replacement else it },
            )
        }
    }
}

/** These rules without the group's own speaker minimum: Chat reads "None" and speaking needs only joining. */
fun ChatRules.withoutSpeakerMinimum(): ChatRules {
    val existing = speakerMinimum ?: return this
    return copy(speaker = speaker.filterNot { it === existing })
}

/**
 * The minimum balance a keypad [entered] amount in [entryRate]'s currency stores, restated in
 * [target] (the listener rule's currency) and rounded to its decimals, or null when it falls below
 * [target]'s minimum transfer value (one unit of its last decimal place, $0.01 or ¥1) or no usable
 * rate can restate it. [targetRate] is only read when [target] differs from both the entry
 * currency and USD.
 */
fun minimumBalanceIn(
    entered: Double,
    entryRate: Rate,
    target: CurrencyCode,
    targetRate: Rate?,
): Fiat? {
    if (entered <= 0.0) return null
    val inTarget = Fiat(entered, entryRate.currency).restatedIn(target, entryRate, targetRate) ?: return null
    val rounded = inTarget.rounded(target.fractionDigits)
    return rounded.takeIf { it >= rounded.smallestUnit }
}

/**
 * This amount in [target], through USD, or null when a rate it needs is missing or unusable.
 * [ownRate] is the rate for this amount's currency, [targetRate] the one for [target]; neither is
 * read when its currency is [target] or USD.
 */
fun Fiat.restatedIn(target: CurrencyCode, ownRate: Rate?, targetRate: Rate?): Fiat? {
    if (currencyCode == target) return this
    val usd = if (currencyCode == CurrencyCode.USD) this else {
        // `Rate.ignore` carries a near-zero fx, which would restate any amount as an enormous USD sum.
        val rate = ownRate?.takeIf { it.isUsable() && it.fx > 0.0 && it.currency == currencyCode } ?: return null
        convertingToUsdIfNeeded(rate)
    }
    if (target == CurrencyCode.USD) return usd
    val rate = targetRate?.takeIf { it.isUsable() && it.fx > 0.0 && it.currency == target } ?: return null
    return usd.convertingTo(rate)
}
