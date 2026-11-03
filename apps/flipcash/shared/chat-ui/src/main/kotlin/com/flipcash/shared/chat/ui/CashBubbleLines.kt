package com.flipcash.shared.chat.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalResources
import com.flipcash.core.R
import com.flipcash.services.models.chat.MessageContent
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color

/**
 * The people a cash bubble names, as the view model resolved them. A null name is one nobody has
 * resolved yet, which the bubble reads as "Someone".
 */
@Immutable
sealed interface CashBubblePeople {
    /** A DM: [other] is the other member, whichever way the money went. */
    data class Direct(val other: String?) : CashBubblePeople

    /**
     * A group: [sender] wrote the message (null when it was the viewer), and [leadRecipient] is the
     * first person it paid, in the order the sender picked them.
     */
    data class Group(val sender: String?, val leadRecipient: String?) : CashBubblePeople
}

/** The line above a cash bubble's amount: who did what. */
@Immutable
sealed interface CashCaption {
    data object YouSent : CashCaption
    data object YouTipped : CashCaption
    data object YouReceived : CashCaption

    /** The viewer was one of a group send's recipients; [others] counts the rest. */
    data class YouAndOthersReceived(val others: Int) : CashCaption

    /** A group member who was not paid reads who sent it. */
    data class SenderSent(val sender: String?) : CashCaption
}

/** The line at the foot of a cash bubble: who it went to, or who it came from. */
@Immutable
sealed interface CashCounterpart {
    data class To(val name: String?) : CashCounterpart

    /** A group send's first recipient and a count of the rest. */
    data class ToLeadAndOthers(val lead: String, val others: Int) : CashCounterpart

    /** A group send whose first recipient has no name yet: a head count instead. */
    data class ToPeople(val count: Int) : CashCounterpart

    data class From(val name: String?) : CashCounterpart
}

@Immutable
data class CashBubbleLines(
    val caption: CashCaption,
    /** Null when the message names nobody on the other side, as a group payment from before receivers were kept. */
    val counterpart: CashCounterpart?,
)

/**
 * The caption and foot line of a cash bubble (`docs/specs/cash-bubbles.md` in the orchestrator).
 *
 * A group send to two or more people names its first recipient to the sender and to members who were
 * not paid, and its sender to the people it paid. A payment to one person reads like a DM to everyone:
 * "to A" for the sender and for members who were not paid, "from <sender>" for the recipient.
 *
 * [viewerPaid] is whether the viewer is among the recipients, which only a group message can say;
 * [people] is null where nobody resolved names for this bubble, which keeps the caption alone.
 */
fun MessageContent.Cash.bubbleLines(
    isFromSelf: Boolean,
    viewerPaid: Boolean,
    people: CashBubblePeople?,
): CashBubbleLines {
    val own = when (action) {
        MessageContent.Cash.Action.TIPPED -> CashCaption.YouTipped
        MessageContent.Cash.Action.SENT -> CashCaption.YouSent
    }
    // The verb splits only on the sending side: a received tip and a received payment are the same
    // money arriving.
    val captionOnly = CashBubbleLines(if (isFromSelf) own else CashCaption.YouReceived, counterpart = null)

    return when (people) {
        null -> captionOnly

        is CashBubblePeople.Direct -> if (isFromSelf) {
            CashBubbleLines(own, CashCounterpart.To(people.other))
        } else {
            CashBubbleLines(CashCaption.YouReceived, CashCounterpart.From(people.other))
        }

        is CashBubblePeople.Group -> {
            val parts = multiReceiver
            when {
                parts.size >= 2 -> {
                    val others = parts.size - 1
                    val toAll = people.leadRecipient
                        ?.let { CashCounterpart.ToLeadAndOthers(it, others) }
                        ?: CashCounterpart.ToPeople(parts.size)
                    when {
                        isFromSelf -> CashBubbleLines(own, toAll)
                        viewerPaid -> CashBubbleLines(
                            CashCaption.YouAndOthersReceived(others),
                            CashCounterpart.From(people.sender),
                        )
                        else -> CashBubbleLines(CashCaption.SenderSent(people.sender), toAll)
                    }
                }

                parts.size == 1 || receiver != null -> when {
                    isFromSelf -> CashBubbleLines(own, CashCounterpart.To(people.leadRecipient))
                    viewerPaid -> CashBubbleLines(CashCaption.YouReceived, CashCounterpart.From(people.sender))
                    else -> CashBubbleLines(
                        CashCaption.SenderSent(people.sender),
                        CashCounterpart.To(people.leadRecipient),
                    )
                }

                else -> captionOnly
            }
        }
    }
}

/** The large line of a cash bubble. */
@Immutable
sealed interface CashAmountLine {
    /** The whole amount: a DM, or a payment to one person. */
    data object Whole : CashAmountLine

    /**
     * What each recipient got. [approx] when the parts differ, and [part] is then the smallest, the
     * amount nobody fell short of.
     */
    data class Each(val part: MessageContent.Cash.Payment, val approx: Boolean) : CashAmountLine
}

/**
 * The large line of a cash bubble. A send to two or more whose parts are all equal reads "$X each".
 * When they differ, as in sends from before every recipient got the same amount, every viewer reads
 * "~$X each" for the smallest part, a recipient included. The parts decide it, not the message's
 * date, so a later send that splits reads the same way.
 */
fun MessageContent.Cash.amountLine(): CashAmountLine {
    val parts = multiReceiver
    if (parts.size < 2) return CashAmountLine.Whole
    val first = parts.first()
    return when {
        parts.all { it.nativeAmount == first.nativeAmount } -> CashAmountLine.Each(first, approx = false)
        else -> CashAmountLine.Each(parts.minBy { it.nativeAmount }, approx = true)
    }
}

/** "$X each", or "~$X each" for [CashAmountLine.Each.approx], with [amount] already formatted. */
fun CashAmountLine.Each.text(resources: Resources, amount: String): String =
    resources.getString(if (approx) R.string.label_groupSendEachApprox else R.string.label_groupSendEach, amount)

@Composable
fun CashCaption.text(): String = text(LocalResources.current)

fun CashCaption.text(resources: Resources): String = when (this) {
    CashCaption.YouSent -> resources.getString(R.string.subtitle_youSent)
    CashCaption.YouTipped -> resources.getString(R.string.subtitle_youTipped)
    CashCaption.YouReceived -> resources.getString(R.string.subtitle_youReceived)
    is CashCaption.YouAndOthersReceived ->
        resources.getQuantityString(R.plurals.subtitle_youAndOthersReceived, others, others)
    is CashCaption.SenderSent -> resources.getString(R.string.subtitle_senderSent, sender.orSomeone(resources))
}

@Composable
fun CashCounterpart.text(): String = text(LocalResources.current)

fun CashCounterpart.text(resources: Resources): String = when (this) {
    is CashCounterpart.To -> resources.getString(R.string.label_groupSendTo, name.orSomeone(resources))
    is CashCounterpart.ToLeadAndOthers -> resources.getString(
        R.string.label_groupSendTo,
        resources.getQuantityString(R.plurals.label_groupSendRecipients, others, lead, others),
    )
    is CashCounterpart.ToPeople -> resources.getQuantityString(R.plurals.label_groupSendToPeople, count, count)
    is CashCounterpart.From -> resources.getString(R.string.label_cashFrom, name.orSomeone(resources))
}

private fun String?.orSomeone(resources: Resources): String =
    this?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.label_cashSomeone)

/**
 * The "$X each" line with [amount] already formatted inside [line]. A "~" before the amount draws at
 * 0.7.em in the amount's own style. " each" after it draws at 0.5.em, Medium, in [eachColor]. Sizes
 * are in em so the auto-size that shrinks the amount shrinks them too and the line never wraps.
 */
fun eachAmountText(line: String, amount: String, eachColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(line)
        val start = line.indexOf(amount)
        val suffix = if (start < 0) 0 else start + amount.length
        if (start > 0) addStyle(SpanStyle(fontSize = 0.7.em), 0, start)
        addStyle(
            SpanStyle(
                fontSize = 0.5.em,
                fontWeight = FontWeight.Medium,
                color = eachColor,
            ),
            suffix,
            line.length,
        )
    }
