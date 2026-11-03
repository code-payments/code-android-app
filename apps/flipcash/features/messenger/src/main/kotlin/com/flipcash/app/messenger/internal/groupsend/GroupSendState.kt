package com.flipcash.app.messenger.internal.groupsend

import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.times

/**
 * A send from a private group to some of its members, from the amount entry sheet's "Next" to the
 * swipe. Every member checked is paid [amount]: nothing is split.
 *
 * @param amount what each person gets, in the currency it was entered in. Null until entered.
 * @param selected who to pay, in the order they were checked: the order of the payments.
 */
internal data class GroupSendState(
    val viewerId: ID?,
    val stage: Stage = Stage.Amount,
    val amount: Fiat? = null,
    val selected: List<ID> = emptyList(),
) {
    enum class Stage { Amount, People, Confirm }

    /** What the sender pays: [amount] for each of [selected]. Null until [amount] is entered. */
    val total: Fiat?
        get() = amount?.let { it * selected.size }

    /** Who Send To lists: the roster without the sender, who cannot be paid. */
    fun candidates(roster: List<ChatMember>): List<ChatMember> =
        roster.filter { it.userId != viewerId }.distinctBy { it.userId }

    /** [selected] with [member] checked or unchecked, or null when checking them would make an 11th. */
    fun toggled(member: ID): List<ID>? = when {
        member in selected -> selected.filterNot { it == member }
        selected.size >= MAX_RECIPIENTS -> null
        else -> selected.plusElement(member)
    }

    companion object {
        const val MAX_RECIPIENTS = 10
    }
}
