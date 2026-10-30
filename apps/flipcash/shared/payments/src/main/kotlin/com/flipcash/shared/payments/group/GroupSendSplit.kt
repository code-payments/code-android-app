package com.flipcash.shared.payments.group

import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import java.math.BigDecimal
import java.math.RoundingMode

/** What a group send should pay, from [GroupSendSplit.split]. */
sealed interface GroupSendPlan {
    /**
     * Pay [parts], in the currency's minor unit and in selection order. One part is a single
     * payment; two or more are one multi-payment.
     */
    data class Pay(val parts: List<Long>) : GroupSendPlan {
        val isEven: Boolean get() = parts.all { it == parts.first() }

        /** The part shown as "Each": every part when [isEven], the smallest otherwise. */
        val smallest: Long get() = parts.min()
    }

    data object NoRecipients : GroupSendPlan

    /** More than [GroupSendSplit.MAX_RECIPIENTS] were selected. */
    data object TooManyRecipients : GroupSendPlan

    /** The total is under [GroupSendSplit.MINIMUM_PART_UNITS] per recipient. */
    data object PartBelowMinimum : GroupSendPlan
}

/**
 * Splits a group send's total between its recipients. A cross-platform parity hotspot: it must
 * agree with iOS at every vector in `test-vectors/group-send-split.json`.
 *
 * The total is split in the currency's minor unit (a cent for USD) before anything is priced. Each
 * part is the total divided by the recipient count, rounded down to that unit, and the leftover
 * goes one unit each to the first recipients in selection order, so the parts add up to the total
 * exactly.
 */
object GroupSendSplit {
    const val MAX_RECIPIENTS = 10
    const val MINIMUM_PART_UNITS = 1L

    /**
     * @param maxRecipients the most the total may be split between. A split that includes the sender
     * counts one more than the people paid, so it allows [MAX_RECIPIENTS] + 1.
     */
    fun split(totalUnits: Long, recipients: Int, maxRecipients: Int = MAX_RECIPIENTS): GroupSendPlan {
        if (recipients <= 0) return GroupSendPlan.NoRecipients
        if (recipients > maxRecipients) return GroupSendPlan.TooManyRecipients
        val base = totalUnits / recipients
        if (base < MINIMUM_PART_UNITS) return GroupSendPlan.PartBelowMinimum
        val leftover = totalUnits % recipients
        return GroupSendPlan.Pay(List(recipients) { i -> if (i < leftover) base + 1 else base })
    }

    /**
     * A split that the sender is part of but is not paid: [totalUnits] is divided between the
     * [paid] members and the sender, who is checked at 0-based position [viewerAt] in check order
     * (null when not checked), then the sender's part is dropped. The result's parts are the paid
     * members', in check order. The too-small and recipient-count rules apply to the split with the
     * sender in it, which may be [MAX_RECIPIENTS] + 1.
     */
    fun splitWithViewer(totalUnits: Long, paid: Int, viewerAt: Int?): GroupSendPlan {
        if (paid <= 0) return GroupSendPlan.NoRecipients
        val split = split(
            totalUnits,
            paid + if (viewerAt != null) 1 else 0,
            maxRecipients = MAX_RECIPIENTS + if (viewerAt != null) 1 else 0,
        )
        if (split !is GroupSendPlan.Pay || viewerAt == null) return split
        return GroupSendPlan.Pay(split.parts.filterIndexed { i, _ -> i != viewerAt })
    }

    /** [fiat] in its currency's minor unit, rounded half-up to that unit. */
    fun minorUnits(fiat: Fiat): Long =
        BigDecimal.valueOf(fiat.quarks)
            .movePointLeft(FIAT_QUARK_DIGITS)
            .setScale(fiat.currencyCode.fractionDigits, RoundingMode.HALF_UP)
            .unscaledValue()
            .longValueExact()

    /** [units] minor units of [currency] as a [Fiat], without going through a double. */
    fun fiat(units: Long, currency: CurrencyCode): Fiat = Fiat(
        stringAmount = BigDecimal.valueOf(units, currency.fractionDigits).toPlainString(),
        currencyCode = currency,
    )

    /** [Fiat.quarks] are millionths of a unit. */
    private const val FIAT_QUARK_DIGITS = 6
}
