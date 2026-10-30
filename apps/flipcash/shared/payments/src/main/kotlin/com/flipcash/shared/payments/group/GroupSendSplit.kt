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
     * payment; two or more are one multi-payment. [viewerPart] is the sender's own part when they
     * are in the split: not paid, and kept out of [parts].
     */
    data class Pay(val parts: List<Long>, val viewerPart: Long? = null) : GroupSendPlan {
        /** Whether the sender is in the split: their part was dropped, and stays with them. */
        val includesViewer: Boolean get() = viewerPart != null

        /** Every part equal, the sender's included, so "Each" needs no "~". */
        val isEven: Boolean get() = (parts + listOfNotNull(viewerPart)).all { it == parts.first() }

        /** Whether the confirm sheet has an "Each" line: with several paid, or to say You are in. */
        val showsEach: Boolean get() = parts.size >= 2 || includesViewer

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

    fun split(totalUnits: Long, recipients: Int): GroupSendPlan {
        if (recipients <= 0) return GroupSendPlan.NoRecipients
        if (recipients > MAX_RECIPIENTS) return GroupSendPlan.TooManyRecipients
        val base = totalUnits / recipients
        if (base < MINIMUM_PART_UNITS) return GroupSendPlan.PartBelowMinimum
        val leftover = totalUnits % recipients
        return GroupSendPlan.Pay(List(recipients) { i -> if (i < leftover) base + 1 else base })
    }

    /**
     * A split that the sender is part of but is not paid: [totalUnits] is divided between the
     * [paid] members and the sender, whose part is always the last share, so they never take an
     * extra unit. The sender's part is then dropped: the result's parts are the paid members',
     * in check order. [includeViewer] false is a plain split. The sender counts toward
     * [MAX_RECIPIENTS], and in the too-small rule.
     */
    fun splitWithViewer(totalUnits: Long, paid: Int, includeViewer: Boolean): GroupSendPlan {
        if (paid <= 0) return GroupSendPlan.NoRecipients
        val split = split(totalUnits, paid + if (includeViewer) 1 else 0)
        if (split !is GroupSendPlan.Pay || !includeViewer) return split
        return GroupSendPlan.Pay(parts = split.parts.dropLast(1), viewerPart = split.parts.last())
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
