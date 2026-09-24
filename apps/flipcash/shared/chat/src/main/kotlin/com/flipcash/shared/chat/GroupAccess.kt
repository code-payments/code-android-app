package com.flipcash.shared.chat

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.sum
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
         */
        fun evaluate(
            isMember: Boolean,
            rules: ChatRules?,
            balances: List<TokenWithBalance>,
            isStaff: Boolean,
        ): GroupAccess {
            if (isMember) return Membered
            val listener = rules?.listener.orEmpty()
            if (listener.isEmpty()) return Eligible

            // Keyed by bytes, not by the key object: `class Mint(bytes) : PublicKey(bytes)`
            // (libs/encryption/keys/.../Mint.kt), so the `PublicKey`s in `mints` are not `Mint`s and
            // a map keyed by `Mint` would miss every one of them.
            val byMint: Map<List<Byte>, Fiat> =
                balances.associate { it.token.address.bytes to it.balance }

            val unmet = listener.firstOrNull { requirement ->
                when (requirement) {
                    is ChatRuleRequirement.MinimumBalance -> {
                        val held = if (requirement.mints.isEmpty()) {
                            byMint.values.sum()
                        } else {
                            requirement.mints.mapNotNull { byMint[it.bytes] }.maxOrNull()
                        }
                        // Compared at display precision, the held side rounded half-up to cents
                        // (`Fiat.toDouble`), and a total rounded once, after adding: a balance the wallet shows as $5.00 meets a $5 bar even
                        // when its exact worth is $4.998, and $4.995 passes too. A launchpad
                        // holding's exact worth depends on the supply this client last saw, and one
                        // that lags a buy prices the new tokens a fraction below what was paid. The
                        // server enforces the rule against its own supply, so admitting half a cent
                        // too generously costs one denied join. iOS's `ConversationGate.unmetBalance`
                        // rounds the same way; keep the two in step.
                        held == null || held.toDouble() < requirement.amount.decimalValue
                    }
                    // `UserFlags.is_staff` is the same field the rule is written against, and the
                    // client already has it — so staff are eligible for a staff chat and can rejoin
                    // one they left. Everyone else is blocked with nothing to buy, which is the arm
                    // that gets a disabled button rather than a purchase they cannot make.
                    ChatRuleRequirement.Staff -> !isStaff
                }
            }

            return if (unmet == null) Eligible else Blocked(unmet)
        }
    }
}

/**
 * [GroupAccess.evaluate] over the live balance and the live staff flag.
 *
 * [isStaff] arrives as a flow rather than a value because it resolves after the screen does — the
 * flags are fetched, so a snapshot read taken while the gate is first decided would say no and
 * never correct itself. A plain `Flow<Boolean>` rather than the flags coordinator keeps this module
 * off `shared:userflags`; callers pass `resolvedFlags.map { it.isStaff.effectiveValue }`.
 *
 * `distinctUntilChanged` because `tokenBalances` re-emits on every price tick, and the gate only
 * ever has three answers — without it the transcript's blur would recompose several times a second
 * to arrive at the value it already had.
 */
fun TokenCoordinator.groupAccess(
    isMember: Boolean,
    rules: ChatRules?,
    isStaff: Flow<Boolean>,
): Flow<GroupAccess> = combine(tokenBalances, isStaff) { balances, staff ->
    GroupAccess.evaluate(
        isMember = isMember,
        rules = rules,
        balances = balances,
        isStaff = staff,
    )
}
    .distinctUntilChanged()
