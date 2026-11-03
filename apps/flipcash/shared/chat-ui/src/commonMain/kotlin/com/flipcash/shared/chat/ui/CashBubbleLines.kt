package com.flipcash.shared.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_cashFrom
import com.flipcash.core.resources.label_cashSomeone
import com.flipcash.core.resources.label_cashToYou
import com.flipcash.core.resources.label_groupSendEach
import com.flipcash.core.resources.label_groupSendEachApprox
import com.flipcash.core.resources.label_groupSendRecipients
import com.flipcash.core.resources.label_groupSendTo
import com.flipcash.core.resources.label_groupSendToPeople
import com.flipcash.core.resources.subtitle_senderSent
import com.flipcash.core.resources.subtitle_senderSentBack
import com.flipcash.core.resources.subtitle_youAndOthersReceived
import com.flipcash.core.resources.subtitle_youReceived
import com.flipcash.core.resources.subtitle_youSent
import com.flipcash.core.resources.subtitle_youSentBack
import com.flipcash.core.resources.subtitle_youTipped
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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

    /** The viewer sent this payment back: it returns an opening payment they were paid. */
    data object YouSentBack : CashCaption

    /** [sender] sent this payment back to the viewer. */
    data class SenderSentBack(val sender: String?) : CashCaption
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

    /** A returned payment, read by the person it was returned to. */
    data object ToYou : CashCounterpart
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
    // A return names itself on both sides, and reads "to you" rather than "from" for its receiver:
    // the money is going back, not arriving (Send Back spec, "The return message").
    if (paymentReturn?.returnOf != null && people !is CashBubblePeople.Group) {
        val other = (people as? CashBubblePeople.Direct)?.other
        return if (isFromSelf) {
            CashBubbleLines(CashCaption.YouSentBack, people?.let { CashCounterpart.To(other) })
        } else {
            CashBubbleLines(CashCaption.SenderSentBack(other), people?.let { CashCounterpart.ToYou })
        }
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

/*
 * Each line's wording is resolved once, by the inline functions below, against whichever string
 * source the caller has: the composables read `stringResource`, and a caller outside composition
 * (a test) passes blocking lookups. Inline, so the composable lookups stay legal in their lambdas.
 */

/** "$X each", or "~$X each" for [CashAmountLine.Each.approx], with [amount] already formatted. */
@Composable
fun CashAmountLine.Each.text(amount: String): String = resolve(
    amount,
    string = { res, args -> stringResource(res, *args) },
)

@Composable
fun CashCaption.text(): String = resolve(
    string = { res, args -> stringResource(res, *args) },
    plural = { res, quantity, args -> pluralStringResource(res, quantity, *args) },
)

@Composable
fun CashCounterpart.text(): String = resolve(
    string = { res, args -> stringResource(res, *args) },
    plural = { res, quantity, args -> pluralStringResource(res, quantity, *args) },
)

internal inline fun CashAmountLine.Each.resolve(
    amount: String,
    string: (StringResource, Array<out Any>) -> String,
): String = string(if (approx) Res.string.label_groupSendEachApprox else Res.string.label_groupSendEach, arrayOf<Any>(amount))

internal inline fun CashCaption.resolve(
    string: (StringResource, Array<out Any>) -> String,
    plural: (PluralStringResource, Int, Array<out Any>) -> String,
): String = when (this) {
    CashCaption.YouSent -> string(Res.string.subtitle_youSent, emptyArray<Any>())
    CashCaption.YouTipped -> string(Res.string.subtitle_youTipped, emptyArray<Any>())
    CashCaption.YouReceived -> string(Res.string.subtitle_youReceived, emptyArray<Any>())
    is CashCaption.YouAndOthersReceived ->
        plural(Res.plurals.subtitle_youAndOthersReceived, others, arrayOf<Any>(others))
    is CashCaption.SenderSent -> string(Res.string.subtitle_senderSent, arrayOf<Any>(sender.orSomeone(string)))
    CashCaption.YouSentBack -> string(Res.string.subtitle_youSentBack, emptyArray<Any>())
    is CashCaption.SenderSentBack ->
        string(Res.string.subtitle_senderSentBack, arrayOf<Any>(sender.orSomeone(string)))
}

internal inline fun CashCounterpart.resolve(
    string: (StringResource, Array<out Any>) -> String,
    plural: (PluralStringResource, Int, Array<out Any>) -> String,
): String = when (this) {
    is CashCounterpart.To -> string(Res.string.label_groupSendTo, arrayOf<Any>(name.orSomeone(string)))
    is CashCounterpart.ToLeadAndOthers -> string(
        Res.string.label_groupSendTo,
        arrayOf<Any>(plural(Res.plurals.label_groupSendRecipients, others, arrayOf<Any>(lead, others))),
    )
    is CashCounterpart.ToPeople -> plural(Res.plurals.label_groupSendToPeople, count, arrayOf<Any>(count))
    is CashCounterpart.From -> string(Res.string.label_cashFrom, arrayOf<Any>(name.orSomeone(string)))
    CashCounterpart.ToYou -> string(Res.string.label_cashToYou, emptyArray<Any>())
}

private inline fun String?.orSomeone(string: (StringResource, Array<out Any>) -> String): String =
    this?.takeIf { it.isNotBlank() } ?: string(Res.string.label_cashSomeone, emptyArray<Any>())

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
