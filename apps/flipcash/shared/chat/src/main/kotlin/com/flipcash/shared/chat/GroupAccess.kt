package com.flipcash.shared.chat

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.flipcash.services.models.chat.blocksReactions
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.sum
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What this viewer may do in this group.
 *
 * One value for the whole screen, because the transcript's blur, the composer's replacement and the
 * button's label are three views of the same answer and must never disagree.
 *
 * It answers the creator's question too. "Would I be eligible to join this?" is what a creator must
 * be able to say yes to before the server will accept the rules they are setting — `StartChat`
 * returns `RULES_NOT_SATISFIED` to a caller who does not meet their own bar. That makes the create
 * form's Create-button check and the join screen's gate the same predicate rather than two that have
 * to be kept in step, which is why this sits in `shared/chat` and not in either feature.
 */
sealed interface GroupAccess {

    /** The viewer is in the chat. Everything is open. */
    data object Membered : GroupAccess

    /** Not in the chat, but nothing is standing in the way of joining it. */
    data object Eligible : GroupAccess

    /** Not in the chat, and [unmet] is what is standing in the way. */
    data class Blocked(val unmet: ChatRuleRequirement) : GroupAccess

    /**
     * Not in the chat, and nothing is known to stand in the way, but only because a minimum
     * balance is in a currency with no rate to restate it in USD. The answer is a guess, so the
     * transcript stays blurred until a rate arrives and turns this into [Eligible] or [Blocked].
     *
     * Mirrors iOS's `ConversationGate.isProvisional` and the `.undetermined` presentation it
     * leads to.
     */
    data object Undetermined : GroupAccess

    companion object {
        /**
         * The gate, as a pure function of the three things that decide it.
         *
         * Membership wins outright. Re-checking the rules for a member would blur the transcript of
         * a chat they are already in the moment their balance dipped below the bar they joined
         * over — the server does not remove them for that, so neither does the client.
         *
         * Only *listener* rules are checked. Speaker rules gate sending inside a chat the viewer can
         * already read, which is a different screen state and not this one.
         *
         * [ChatRuleRequirement.Staff] is satisfied by `UserFlags.is_staff`, which is the field the
         * rule names and one the client is already told. Treating it as never satisfiable locked
         * staff out of their own chats once they left one.
         *
         * A [ChatRuleRequirement.MinimumBalance] is satisfied by the largest single balance among
         * the mints it names, not by their sum: the requirement is "hold $100 of this", and two
         * unrelated $60 positions are not that. An empty `mints` names no token in particular and
         * is measured against everything held, added up, as iOS's `ConversationGate.unmetBalance`
         * does with `totalBalance`.
         *
         * A minimum balance in a currency other than USD is restated in USD through [rates]. With
         * no rate for it, the rule counts as met (see [isUnmet]) and the answer is [Undetermined]
         * rather than [Eligible], unless another rule already blocks. [Blocked] wins because it is
         * known without the missing rate.
         */
        fun evaluate(
            isMember: Boolean,
            rules: ChatRules?,
            balances: List<TokenWithBalance>,
            isStaff: Boolean,
            rates: Map<CurrencyCode, Rate> = emptyMap(),
        ): GroupAccess {
            if (isMember) return Membered
            val listener = rules?.listener.orEmpty()
            if (listener.isEmpty()) return Eligible

            val unmet = listener.firstOrNull { it.isUnmet(balances, isStaff, rates) }
            if (unmet != null) return Blocked(unmet)

            val provisional = listener.any {
                it is ChatRuleRequirement.MinimumBalance && it.amount.usdValue(rates) == null
            }
            return if (provisional) Undetermined else Eligible
        }
    }
}

/**
 * [GroupAccess.evaluate] over the live balance, the live staff flag and the live exchange rates.
 *
 * [isStaff] arrives as a flow rather than a value because it resolves after the screen does — the
 * flags are fetched, so a snapshot read taken while the gate is first decided would say no and
 * never correct itself. A plain `Flow<Boolean>` rather than the flags coordinator keeps this module
 * off `shared:userflags`; callers pass `resolvedFlags.map { it.isStaff.effectiveValue }`.
 *
 * [rates] is `Exchange.observeRates()`. A rate arriving is what settles [GroupAccess.Undetermined].
 *
 * `distinctUntilChanged` because `tokenBalances` re-emits on every price tick, and the gate only
 * ever has three answers — without it the transcript's blur would recompose several times a second
 * to arrive at the value it already had.
 */
fun TokenCoordinator.groupAccess(
    isMember: Boolean,
    rules: ChatRules?,
    isStaff: Flow<Boolean>,
    rates: Flow<Map<CurrencyCode, Rate>>,
): Flow<GroupAccess> = combine(tokenBalances, isStaff, rates) { balances, staff, rates ->
    GroupAccess.evaluate(
        isMember = isMember,
        rules = rules,
        balances = balances,
        isStaff = staff,
        rates = rates,
    )
}
    .distinctUntilChanged()

/**
 * Whether the viewer may speak here: every listener rule and every speaker rule holds, as measured
 * by the same predicate the listener gate uses.
 *
 * Speaking implies listening, so the listener bar applies to speaking too. A group with no speaker
 * rules is gated by its listener rules alone, which the server treats as the speaker rules when
 * none are set. This matches iOS's `ConversationGate`, whose speaker verdict is taken over the
 * unmet listener and speaker rules together.
 *
 * Independent of [GroupAccess] on purpose. That type answers "may this viewer read and join", and
 * membership settles it before any rule is looked at; speaking is a second question asked of
 * someone already in the chat. A member whose balance has dipped below the listener or speaker bar
 * is still a member and can still read, but may not post. [ChatRuleRequirement.Never] is never
 * satisfied, so a chat with a `never` speaker rule is read-only for everyone.
 *
 * Callers combine this with membership themselves: a chat the viewer is outside of has no
 * composer to gate, and whether they may post there is already decided by `canPost`.
 */
fun canSpeak(
    rules: ChatRules?,
    balances: List<TokenWithBalance>,
    isStaff: Boolean,
    viewerId: ID? = null,
    creatorId: ID? = null,
    rates: Map<CurrencyCode, Rate> = emptyMap(),
): Boolean = unmetSpeakerRequirement(rules, balances, isStaff, viewerId, creatorId, rates) == null

/**
 * The requirement to name when the viewer may not speak, or null when they may.
 *
 * Picks the way iOS's `ConversationGate` does: across the unmet listener rules then the unmet
 * speaker rules, the first minimum balance, since it is the only requirement a viewer can act on,
 * falling back to the first unmet rule. A met rule is never named.
 */
fun unmetSpeakerRequirement(
    rules: ChatRules?,
    balances: List<TokenWithBalance>,
    isStaff: Boolean,
    viewerId: ID? = null,
    creatorId: ID? = null,
    rates: Map<CurrencyCode, Rate> = emptyMap(),
): ChatRuleRequirement? = resolveSpeakerBlock(rules, balances, isStaff, viewerId, creatorId, rates)?.requirement

/**
 * What stands between the viewer and speaking: the requirement to name, and whether any unmet
 * rule also takes reactions away.
 *
 * Posting (the composer and Reply) is withheld whenever a block exists. Reactions are withheld
 * when any listener rule is unmet, or when an unmet speaker rule has [blocksReactions], so
 * `creator` alone leaves them on while `creator` + `staff` turns them off. [reactionsBlocked]
 * looks at every unmet rule, not just the named one, because the named one is chosen for what the
 * viewer can act on.
 */
data class SpeakerBlock(
    val requirement: ChatRuleRequirement,
    val reactionsBlocked: Boolean,
)

/**
 * The [SpeakerBlock] for the viewer, or null when every listener and speaker rule holds.
 *
 * A minimum balance with no rate to restate it counts as met here too, and nothing marks the
 * answer provisional: as on iOS, only the listener gate waits for the rate. A member who is let
 * through on a guess finds out from the server on send.
 */
fun resolveSpeakerBlock(
    rules: ChatRules?,
    balances: List<TokenWithBalance>,
    isStaff: Boolean,
    viewerId: ID? = null,
    creatorId: ID? = null,
    rates: Map<CurrencyCode, Rate> = emptyMap(),
): SpeakerBlock? {
    // Listener rules are measured without the ids, as the listener gate measures them.
    val listenerUnmet = rules?.listener.orEmpty().filter { it.isUnmet(balances, isStaff, rates) }
    val speakerUnmet = rules?.speaker.orEmpty().filter { it.isUnmet(balances, isStaff, rates, viewerId, creatorId) }
    val unmet = listenerUnmet + speakerUnmet
    val named = unmet.firstOrNull { it is ChatRuleRequirement.MinimumBalance } ?: unmet.firstOrNull()
        ?: return null
    // As iOS: any unmet listener rule takes reactions away, whatever kind it is.
    val reactionsBlocked = listenerUnmet.isNotEmpty() || speakerUnmet.any { it.blocksReactions }
    return SpeakerBlock(requirement = named, reactionsBlocked = reactionsBlocked)
}

/**
 * [resolveSpeakerBlock] over the live balance, the live staff flag and the live exchange rates,
 * de-duplicated like [groupAccess].
 *
 * [viewerId] and [creatorId] are plain values: who the viewer is and who made the chat do not
 * change while the chat is open. A null [creatorId] (the chat's metadata did not carry one) leaves
 * a `creator` rule unmet.
 */
fun TokenCoordinator.speakerBlock(
    rules: ChatRules?,
    isStaff: Flow<Boolean>,
    rates: Flow<Map<CurrencyCode, Rate>>,
    viewerId: ID? = null,
    creatorId: ID? = null,
): Flow<SpeakerBlock?> = combine(tokenBalances, isStaff, rates) { balances, staff, rates ->
    resolveSpeakerBlock(
        rules = rules,
        balances = balances,
        isStaff = staff,
        viewerId = viewerId,
        creatorId = creatorId,
        rates = rates,
    )
}
    .distinctUntilChanged()

/**
 * Whether one rule is standing in the viewer's way. Shared by the listener gate and [canSpeak] so
 * a balance or staff bar means the same thing wherever it is written.
 */
private fun ChatRuleRequirement.isUnmet(
    balances: List<TokenWithBalance>,
    isStaff: Boolean,
    rates: Map<CurrencyCode, Rate>,
    viewerId: ID? = null,
    creatorId: ID? = null,
): Boolean {
    return when (this) {
        is ChatRuleRequirement.MinimumBalance -> {
            // Balances are held in USD; a requirement in another currency is restated through
            // its rate first. With no rate it counts as met: deliberately fail-open, as iOS's
            // `ConversationGate.unmetBalance` is. The server enforces the same rule, so a wrong
            // guess costs one denied read, while failing closed would lock out a viewer who
            // qualifies for as long as the rate table lacks that currency.
            // [GroupAccess.Undetermined] is how the listener gate admits it is guessing.
            val required = amount.usdValue(rates) ?: return false
            val held = heldAgainst(mints, balances)
            // Compared at display precision, the held side rounded half-up to cents
            // (`Fiat.toDouble`), and a total rounded once, after adding: a balance the wallet shows as $5.00 meets a $5 bar even
            // when its exact worth is $4.998, and $4.995 passes too. A launchpad
            // holding's exact worth depends on the supply this client last saw, and one
            // that lags a buy prices the new tokens a fraction below what was paid. The
            // server enforces the rule against its own supply, so admitting half a cent
            // too generously costs one denied join. iOS's `ConversationGate.unmetBalance`
            // rounds the same way; keep the two in step.
            held == null || held.toDouble() < required
        }
        // `UserFlags.is_staff` is the same field the rule is written against, and the
        // client already has it — so staff are eligible for a staff chat and can rejoin
        // one they left. Everyone else is blocked with nothing to buy, which is the arm
        // that gets a disabled button rather than a purchase they cannot make.
        ChatRuleRequirement.Staff -> !isStaff
        // Nobody satisfies it. The server sends it as a speaker rule only, where it is what
        // makes a chat read-only for everyone; as a listener rule it would lock everyone out.
        ChatRuleRequirement.Never -> true
        // Met only by the chat's creator. Staff get no bypass. With no creator on the metadata, or
        // no viewer, nobody can be shown to be the creator, so it stays closed. As a listener rule
        // (the server never sends one) the ids are not passed and it is unmet.
        ChatRuleRequirement.Creator -> creatorId == null || viewerId == null || viewerId != creatorId
        // A rule this build cannot read: unmet for everyone, staff included.
        ChatRuleRequirement.UnsupportedSpeakerRule -> true
    }
}

/**
 * What the viewer holds toward a minimum balance naming [mints], in USD, or null when [mints] is
 * non-empty and none of them is held.
 *
 * The largest single balance among the named mints, not their sum: the requirement is "hold $100 of
 * this", and two unrelated $60 positions are not that. An empty [mints] names no token in
 * particular and is measured against everything held, added up. This is the one measurement the
 * gate ([isUnmet]) and a screen that shows the viewer's own balance both read.
 */
fun heldAgainst(mints: List<PublicKey>, balances: List<TokenWithBalance>): Fiat? {
    // Keyed by bytes, not by the key object: `class Mint(bytes) : PublicKey(bytes)`
    // (libs/encryption/keys/.../Mint.kt), so the `PublicKey`s in `mints` are not `Mint`s and
    // a map keyed by `Mint` would miss every one of them.
    val byMint: Map<List<Byte>, Fiat> =
        balances.associate { it.token.address.bytes to it.balance }
    return if (mints.isEmpty()) {
        byMint.values.sum()
    } else {
        mints.mapNotNull { byMint[it.bytes] }.maxOrNull()
    }
}

/**
 * Every unmet rule in the order iOS's speaker verdict lists them: the listener rules first, then
 * the speaker rules. Empty when the viewer may speak.
 *
 * [resolveSpeakerBlock] names the first minimum balance for the composer; a caller that needs the
 * last one (the chat's own bar, which also covers the join bar) reads it from here.
 */
fun unmetRequirements(
    rules: ChatRules?,
    balances: List<TokenWithBalance>,
    isStaff: Boolean,
    viewerId: ID? = null,
    creatorId: ID? = null,
    rates: Map<CurrencyCode, Rate> = emptyMap(),
): List<ChatRuleRequirement> {
    val listenerUnmet = rules?.listener.orEmpty().filter { it.isUnmet(balances, isStaff, rates) }
    val speakerUnmet = rules?.speaker.orEmpty().filter { it.isUnmet(balances, isStaff, rates, viewerId, creatorId) }
    return listenerUnmet + speakerUnmet
}

/**
 * This amount in USD, or null when it is in another currency with no usable rate. A [Rate]'s `fx`
 * is units of its currency per US dollar, so the USD worth is the amount divided by it.
 */
fun Fiat.usdValue(rates: Map<CurrencyCode, Rate>): Double? {
    if (currencyCode == CurrencyCode.USD) return decimalValue
    val fx = rates[currencyCode]?.takeIf { it.isUsable() }?.fx?.takeIf { it > 0.0 } ?: return null
    return decimalValue / fx
}
