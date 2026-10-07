package com.flipcash.app.messenger.internal.screens.profile

import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.SampledChatter
import com.flipcash.shared.chat.GroupAccess
import com.flipcash.shared.chat.heldAgainst
import com.flipcash.shared.chat.unmetRequirements
import com.flipcash.shared.chat.usdValue
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.solana.keys.Mint
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The one action pinned to the bottom of a group's profile.
 *
 * Mirrors iOS's `GroupProfileCTA`. Resolved from membership and the unmet rules rather than from
 * the transcript's gate, because the two answer different questions: the gate decides what the
 * transcript may show, this decides what the viewer can do next from here.
 */
internal sealed interface GroupProfileCta {
    /** Nothing to offer: the answer is not known yet, or the bar is not one a viewer can clear. */
    data object None : GroupProfileCta
    data object Join : GroupProfileCta
    data object OpenChat : GroupProfileCta

    /** Not a member, and short of the join minimum. */
    data class BuyToJoin(val requirement: ChatRuleRequirement.MinimumBalance) : GroupProfileCta

    /** A member, and short of the minimum that lets them speak. */
    data class BuyToChat(val requirement: ChatRuleRequirement.MinimumBalance) : GroupProfileCta

    companion object {
        /**
         * @param access what the listener gate says; [GroupAccess.Undetermined] is iOS's
         * `isProvisional`, a guess that offers nothing until a rate arrives.
         * @param unmet every unmet rule in the order [unmetRequirements] lists them: listener rules
         * first, then speaker rules.
         */
        fun resolve(
            isMember: Boolean,
            access: GroupAccess,
            unmet: List<ChatRuleRequirement>,
        ): GroupProfileCta {
            if (isMember) {
                // The last minimum, not the first: the speaker rules come after the listener ones,
                // so it is the chat minimum, and it is the larger bar when both are unmet. A
                // member under only the join minimum is told to buy to chat, as on iOS. A chat
                // that is read-only, staff-only or creator-only has nothing to buy, so it opens.
                val minimum = unmet.filterIsInstance<ChatRuleRequirement.MinimumBalance>().lastOrNull()
                return if (minimum != null) BuyToChat(minimum) else OpenChat
            }
            return when (access) {
                GroupAccess.Undetermined -> None
                GroupAccess.Eligible, GroupAccess.Membered -> Join
                is GroupAccess.Blocked -> {
                    val minimum = access.unmet as? ChatRuleRequirement.MinimumBalance
                    if (minimum != null) BuyToJoin(minimum) else None
                }
            }
        }
    }
}

/**
 * The balance a group asks for, as the two rows the profile shows. Null when there is no minimum
 * to show on either row.
 *
 * Only the first minimum of each list counts; staff, never and creator rules have no amount.
 * [chat] falls back to [join] when no speaker minimum is set, because the server treats the
 * listener rules as the speaker rules then. A chat-only minimum leaves [join] null.
 */
internal data class GroupBalanceRequirements(
    val join: ChatRuleRequirement.MinimumBalance?,
    val chat: ChatRuleRequirement.MinimumBalance?,
) {
    /**
     * The one token the requirements name, or null when they name none, two different ones, or
     * only the reserve. A requirement naming no mint ("any holding") does not count against a
     * token the other names. Mirrors iOS's `GroupBalanceRequirements.soleToken`.
     */
    val soleToken: Mint?
        get() {
            val mints = listOfNotNull(join, chat)
                .mapNotNull { it.mints.firstOrNull()?.let { mint -> Mint(mint.bytes) } }
                .toSet()
            return mints.singleOrNull()?.takeUnless { it == Mint.usdf }
        }

    companion object {
        fun from(rules: ChatRules?): GroupBalanceRequirements? {
            val join = rules?.listener.orEmpty()
                .filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
            val speaker = rules?.speaker.orEmpty()
                .filterIsInstance<ChatRuleRequirement.MinimumBalance>().firstOrNull()
            if (join == null && speaker == null) return null
            return GroupBalanceRequirements(join = join, chat = speaker ?: join)
        }
    }
}

/**
 * What the viewer still needs to buy to meet [requirement], in the requirement's own currency, or
 * null when they already meet it or when the requirement cannot be restated (no rate for its
 * currency). A null falls back to showing the full requirement.
 *
 * Compared in USD at display precision, as the gate does: the held side is rounded half-up to
 * cents. A holding of the named mint counts alone; a requirement naming no mint is measured
 * against everything held. The result is rounded up to the currency's smallest unit, so a buy of
 * exactly this much clears the bar.
 */
internal fun balanceShortfall(
    requirement: ChatRuleRequirement.MinimumBalance,
    balances: List<TokenWithBalance>,
    rates: Map<CurrencyCode, Rate>,
): Fiat? {
    val required = requirement.amount.usdValue(rates) ?: return null
    val held = heldAgainst(requirement.mints, balances)?.toDouble() ?: 0.0
    if (held >= required) return null
    val shortUsd = BigDecimal.valueOf(required).subtract(BigDecimal.valueOf(held))
        .setScale(MICRO_SCALE, RoundingMode.HALF_UP)
    val currency = requirement.amount.currencyCode
    val short = if (currency == CurrencyCode.USD) {
        shortUsd
    } else {
        val fx = rates[currency]?.takeIf { it.isUsable() }?.fx?.takeIf { it > 0.0 } ?: return null
        shortUsd.multiply(BigDecimal.valueOf(fx)).setScale(MICRO_SCALE, RoundingMode.HALF_UP)
    }
    return Fiat(fiat = short.setScale(currency.fractionDigits, RoundingMode.CEILING).toDouble(), currencyCode = currency)
}

/**
 * The "Your Balance" line: what the viewer holds toward the join minimum (the chat minimum when
 * there is none), in that requirement's currency. Null when it cannot be stated, which is a
 * requirement in a currency with no rate.
 *
 * Measured by [heldAgainst], the same measurement the gate uses.
 */
internal fun yourBalance(
    requirements: GroupBalanceRequirements,
    balances: List<TokenWithBalance>,
    rates: Map<CurrencyCode, Rate>,
): Fiat? {
    val rule = requirements.join ?: requirements.chat ?: return null
    val heldUsd = (heldAgainst(rule.mints, balances) ?: Fiat.Zero).toDouble()
    val currency = rule.amount.currencyCode
    val value = if (currency == CurrencyCode.USD) {
        heldUsd
    } else {
        val fx = rates[currency]?.takeIf { it.isUsable() }?.fx?.takeIf { it > 0.0 } ?: return null
        heldUsd * fx
    }
    return Fiat(fiat = value, currencyCode = currency).rounded(currency.fractionDigits)
}

/** The Chatting section: only for a public group with someone to show. */
internal fun isChattingGridVisible(isPrivate: Boolean, chatters: List<SampledChatter>): Boolean =
    !isPrivate && chatters.isNotEmpty()

/**
 * The profile's pinned action and the figures behind it, decided together so they are re-decided
 * together when a balance or rate moves.
 */
internal data class GroupProfileStanding(
    val cta: GroupProfileCta,
    /** The shortfall a buy CTA names; null falls back to the requirement's full amount. */
    val shortfall: Fiat?,
    /** The "Your Balance" line, when it can be stated. */
    val yourBalance: Fiat?,
    /** Where the gate's Buy goes: the buy screen, or straight to Add Money. */
    val funding: GateFunding = GateFunding.Buy,
) {
    companion object {
        fun of(
            isMember: Boolean,
            rules: ChatRules?,
            balances: List<TokenWithBalance>,
            isStaff: Boolean,
            rates: Map<CurrencyCode, Rate>,
            viewerId: ID?,
            creatorId: ID?,
        ): GroupProfileStanding {
            val access = GroupAccess.evaluate(isMember, rules, balances, isStaff, rates)
            val unmet = unmetRequirements(rules, balances, isStaff, viewerId, creatorId, rates)
            val cta = GroupProfileCta.resolve(isMember, access, unmet)
            val bar = when (cta) {
                is GroupProfileCta.BuyToJoin -> cta.requirement
                is GroupProfileCta.BuyToChat -> cta.requirement
                else -> null
            }
            return GroupProfileStanding(
                cta = cta,
                shortfall = bar?.let { balanceShortfall(it, balances, rates) },
                yourBalance = GroupBalanceRequirements.from(rules)?.let { yourBalance(it, balances, rates) },
                funding = bar?.let { gateFunding(it, balances, rates) } ?: GateFunding.Buy,
            )
        }
    }
}

/** Where the gate's Buy goes. */
internal enum class GateFunding { Buy, AddMoney }

/**
 * Whether a buy of [requirement]'s token can be paid for, mirroring iOS
 * `ConversationScreen.addFunds()`. The buy flow pays from one balance, so it can only go ahead
 * when a single held balance covers the shortfall; otherwise the viewer is sent to Add Money
 * instead of finding out on the buy screen. Payment sources are the buy flow's: displayable
 * balances, minus the gated token itself (the server rejects a same-mint swap). Both sides are
 * in USD. With no rate to state the shortfall, it falls back to the buy screen's own rule: any
 * spendable source at all.
 *
 * A rule on dollars, or on no single mint, has no token to buy and always adds cash; this is
 * only consulted for a named token.
 */
internal fun gateFunding(
    requirement: ChatRuleRequirement.MinimumBalance,
    balances: List<TokenWithBalance>,
    rates: Map<CurrencyCode, Rate>,
): GateFunding {
    val gated = requirement.mints.firstOrNull()?.bytes
    val sources = balances.filter { it.balance.hasDisplayableValue && it.token.address.bytes != gated }
    val required = requirement.amount.usdValue(rates)
        ?: return if (sources.isEmpty()) GateFunding.AddMoney else GateFunding.Buy
    val held = heldAgainst(requirement.mints, balances)?.toDouble() ?: 0.0
    val shortUsd = (required - held).coerceAtLeast(0.0)
    return if (sources.any { it.balance.toDouble() >= shortUsd }) GateFunding.Buy else GateFunding.AddMoney
}

private const val MICRO_SCALE = 6
