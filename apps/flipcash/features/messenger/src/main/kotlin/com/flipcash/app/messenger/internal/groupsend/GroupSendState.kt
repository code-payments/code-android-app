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

    /**
     * Who is paid: [selected] without the sender. The sender can be checked to take a part of the
     * split, but the contract requires every receiver to be another member.
     */
    val paid: List<ID>
        get() = selected.filterNot { it == viewerId }

    /** Whether the sender is checked, which counts them in the split without paying them. */
    val includesViewer: Boolean
        get() = viewerId != null && viewerId in selected

    /**
     * The parts to pay, once [total] is known and someone other than the sender is checked: the
     * total is split between everyone checked, the sender included, in check order, then the
     * sender's part is dropped.
     */
    val plan: GroupSendPlan
        get() {
            val total = total ?: return GroupSendPlan.NoRecipients
            val viewerAt = selected.indexOf(viewerId).takeIf { it >= 0 }
            return GroupSendSplit.splitWithViewer(GroupSendSplit.minorUnits(total), paid.size, viewerAt)
        }

    /** Who Select People lists: the roster without the sender, who has a "You" row of their own. */
    fun candidates(roster: List<ChatMember>): List<ChatMember> =
        roster.filter { it.userId != viewerId }.distinctBy { it.userId }

    /** The sender's own row, from the roster, when they are in it. */
    fun viewer(roster: List<ChatMember>): ChatMember? = roster.find { it.userId == viewerId }

    /**
     * [selected] with [member] checked or unchecked, or null when checking them would make an 11th
     * paid member. The sender does not count toward the cap: they are not paid.
     */
    fun toggled(member: ID): List<ID>? = when {
        member in selected -> selected.filterNot { it == member }
        member != viewerId && paid.size >= GroupSendSplit.MAX_RECIPIENTS -> null
        else -> selected.plusElement(member)
    }
}
