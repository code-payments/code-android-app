package com.flipcash.app.messenger.internal.request

import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.IdempotencyKey
import com.flipcash.services.models.paymentrequest.NewSplitRequest
import com.flipcash.services.models.paymentrequest.PaymentRequestText
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A payment request a creator is putting together in a private group, from Amount Per Person to
 * the confirm sheet. Frames 04–06 of the group requests spec.
 *
 * @param members who Select People lists: the creator first, then the rest of the roster in its
 *   order. Shares are sent in this order, whatever order the boxes were checked in.
 * @param selected who is asked, the creator included when [viewerId] is in it.
 * @param idempotencyKey minted when the confirm sheet first opens, and reused by every retry of
 *   that send.
 * @param createdRequestId set once Create returns OK. From then on only the message send is
 *   retried; Create is never called again for this confirm.
 */
internal data class NewRequestDraft(
    val viewerId: ID,
    val members: List<ID>,
    val title: String,
    val perPerson: Fiat? = null,
    val selected: Set<ID> = if (members.size > MAX_SHARES) emptySet() else members.toSet(),
    val deadline: Deadline = Deadline.Week,
    val idempotencyKey: IdempotencyKey? = null,
    val createdRequestId: ID? = null,
) {
    /** Members asked other than the creator: the N in "Request $20 from N People". */
    val requestedCount: Int get() = selected.count { it != viewerId }

    val includesCreator: Boolean get() = viewerId in selected

    /** Select People's button: at least one member other than the creator. */
    val canContinue: Boolean get() = requestedCount > 0

    val canSend: Boolean get() = canContinue && perPerson != null && title.isNotBlank()

    /** Who is asked, in [members] order. */
    val shares: List<ID> get() = members.filter { it in selected }

    val people: People
        get() {
            val others = members.count { it != viewerId }
            return when {
                selected.isEmpty() -> People.Choose
                includesCreator && requestedCount == others -> People.EveryoneIncludingYou
                !includesCreator && requestedCount == others -> People.EveryoneExceptYou
                includesCreator -> People.SomeIncludingYou(requestedCount)
                else -> People.Some(requestedCount)
            }
        }

    /** [selected] with [member] checked or unchecked, or null when checking them would make a 101st share. */
    fun toggled(member: ID): Set<ID>? = when {
        member in selected -> selected.minusElement(member)
        selected.size >= MAX_SHARES -> null
        else -> selected.plusElement(member)
    }

    /** What Create sends, with [now] the moment Send Request was tapped. */
    fun toSplit(chatId: ChatId, text: PaymentRequestText.Encrypted, now: Instant): NewSplitRequest? {
        val amount = perPerson ?: return null
        if (!canContinue) return null
        return NewSplitRequest(
            chatId = chatId,
            text = text,
            currency = amount.currencyCode,
            endsAt = deadline.endsAt(now),
            shares = shares,
            perPerson = amount,
        )
    }

    /** The summary's People value. The second line shows whenever [markedPaid]. */
    sealed interface People {
        val markedPaid: Boolean get() = false

        data object Choose : People
        data object EveryoneIncludingYou : People { override val markedPaid = true }
        data object EveryoneExceptYou : People
        data class SomeIncludingYou(val others: Int) : People { override val markedPaid = true }
        data class Some(val count: Int) : People
    }

    /** The Time To Pay choice. A preset resolves against the moment Send is tapped, so it never drifts shorter. */
    sealed interface Deadline {
        fun endsAt(now: Instant): Instant

        enum class Preset(val length: Duration, val daysToPay: Int) : Deadline {
            Day(1.days, 1),
            ThreeDays(3.days, 3),
            Week(7.days, 7),
            TwoWeeks(14.days, 14),

            // Five minutes short of the server's limit, so a device clock running slightly ahead still passes.
            FourWeeks(28.days - 5.minutes, 28);

            override fun endsAt(now: Instant): Instant = now + length
        }

        data class Custom(val at: Instant) : Deadline {
            override fun endsAt(now: Instant): Instant = at
        }

        companion object {
            val Week: Deadline = Preset.Week

            /** The custom picker's bounds. */
            fun customRange(now: Instant): ClosedRange<Instant> = (now + 1.hours)..(now + Preset.FourWeeks.length)
        }
    }

    companion object {
        /** At most this many shares, the creator's included. */
        const val MAX_SHARES = 100

        /** The creator first, then the rest of [roster] in order, each once. */
        fun members(viewerId: ID, roster: List<ChatMember>): List<ID> =
            listOf(viewerId) + roster.map { it.userId }.filter { it != viewerId }.distinct()
    }
}
