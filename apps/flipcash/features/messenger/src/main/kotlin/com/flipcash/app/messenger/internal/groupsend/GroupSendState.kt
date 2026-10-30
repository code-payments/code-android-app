package com.flipcash.app.messenger.internal.groupsend

import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.payments.group.GroupSendPlan
import com.flipcash.shared.payments.group.GroupSendSplit
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat

/**
 * A send from a private group to some of its members, from the amount entry sheet's "Next" to the
 * swipe. Node 11293:6918.
 *
 * @param total what the sender entered, in the currency it was entered in. Null until then.
 * @param selected who to pay, in the order they were checked: the order decides the split.
 */
internal data class GroupSendState(
    val viewerId: ID?,
    val stage: Stage = Stage.Amount,
    val total: Fiat? = null,
    val selected: List<ID> = emptyList(),
) {
    enum class Stage { Amount, People, Confirm }

    /** The parts to pay, once [total] is known and someone is checked. */
    val plan: GroupSendPlan
        get() = total?.let { GroupSendSplit.split(GroupSendSplit.minorUnits(it), selected.size) }
            ?: GroupSendPlan.NoRecipients

    /** Who Select People lists: the roster without the sender, who never pays themselves. */
    fun candidates(roster: List<ChatMember>): List<ChatMember> =
        roster.filter { it.userId != viewerId }.distinctBy { it.userId }

    /**
     * [selected] with [member] checked or unchecked, or null when checking them would make an 11th.
     */
    fun toggled(member: ID): List<ID>? = when {
        member in selected -> selected.filterNot { it == member }
        selected.size >= GroupSendSplit.MAX_RECIPIENTS -> null
        else -> selected.plusElement(member)
    }
}
