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
 * @param selected who to pay, in the order they were checked: the order decides the split. Never
 * the sender, who is [includesSender] instead.
 * @param includesSender whether the sender checked "You", splitting [total] with themselves and
 * keeping their part.
 */
internal data class GroupSendState(
    val viewerId: ID?,
    val stage: Stage = Stage.Amount,
    val total: Fiat? = null,
    val selected: List<ID> = emptyList(),
    val includesSender: Boolean = false,
) {
    enum class Stage { Amount, People, Confirm }

    /** The parts to pay, once [total] is known and someone is checked. */
    val plan: GroupSendPlan
        get() = total?.let { GroupSendSplit.split(GroupSendSplit.minorUnits(it), selected.size, includesSender) }
            ?: GroupSendPlan.NoRecipients

    /** How many ways [total] splits: [selected], and the sender when [includesSender]. */
    val shares: Int get() = selected.size + if (includesSender) 1 else 0

    /** The rows Select People shows checked: [selected], and the sender's when [includesSender]. */
    val checked: List<ID> get() = if (includesSender) selected + listOfNotNull(viewerId) else selected

    /** Who Select People lists: the sender first, as "You", then the rest of the roster. */
    fun candidates(roster: List<ChatMember>): List<ChatMember> =
        roster.distinctBy { it.userId }.sortedByDescending { it.userId == viewerId }

    /**
     * This state with [member] checked or unchecked, the sender included, or null when checking
     * them would make an 11th share.
     */
    fun toggled(member: ID): GroupSendState? = when {
        member == viewerId -> when {
            includesSender -> copy(includesSender = false)
            shares >= GroupSendSplit.MAX_RECIPIENTS -> null
            else -> copy(includesSender = true)
        }
        member in selected -> copy(selected = selected.filterNot { it == member })
        shares >= GroupSendSplit.MAX_RECIPIENTS -> null
        else -> copy(selected = selected.plusElement(member))
    }
}
