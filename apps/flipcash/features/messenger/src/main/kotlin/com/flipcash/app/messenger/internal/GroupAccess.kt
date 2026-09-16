package com.flipcash.app.messenger.internal

import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.models.chat.ChatRuleRequirement
import com.flipcash.services.models.chat.ChatRules
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.TokenWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What this viewer may do in this group.
 *
 * One value for the whole screen, because the transcript's blur, the composer's replacement and the
 * button's label are three views of the same answer and must never disagree. Resolved in the feature
 * rather than in `shared/chat`: the chat layer stores the rules and the membership flag, and what
 * they *mean* depends on a token balance that lives on the other side of that module boundary.
 */
internal sealed interface GroupAccess {

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
         * A [ChatRuleRequirement.MinimumBalance] is satisfied by the largest single balance among
         * the mints it names, not by their sum: the requirement is "hold $100 of this", and two
         * unrelated $60 positions are not that. An empty `mints` names no token in particular, so
         * any one of them can satisfy it.
         */
        fun evaluate(
            isMember: Boolean,
            rules: ChatRules?,
            balances: List<TokenWithBalance>,
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
                        val candidates = if (requirement.mints.isEmpty()) {
                            byMint.values
                        } else {
                            requirement.mints.mapNotNull { byMint[it.bytes] }
                        }
                        val best = candidates.maxOrNull()
                        best == null || best < requirement.amount
                    }
                    // Nothing the client can check and nothing the user can go and acquire. A
                    // non-member facing a staff chat is blocked, and Task 9 gives that arm a
                    // disabled button rather than a purchase the user cannot make.
                    ChatRuleRequirement.Staff -> true
                }
            }

            return if (unmet == null) Eligible else Blocked(unmet)
        }
    }
}

/**
 * [GroupAccess.evaluate] over the live balance.
 *
 * `distinctUntilChanged` because `tokenBalances` re-emits on every price tick, and the gate only
 * ever has three answers — without it the transcript's blur would recompose several times a second
 * to arrive at the value it already had.
 */
internal fun TokenCoordinator.groupAccess(
    isMember: Boolean,
    rules: ChatRules?,
): Flow<GroupAccess> = tokenBalances
    .map { GroupAccess.evaluate(isMember = isMember, rules = rules, balances = it) }
    .distinctUntilChanged()
