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
     *
     * @param kept the sender's own part when they split the total with themselves: the last share,
     * never sent. Null when they didn't.
     */
    data class Pay(val parts: List<Long>, val kept: Long? = null) : GroupSendPlan {
        /** Every part of the total, the sender's included: what "Each" and its "~" describe. */
        val shares: List<Long> get() = parts + listOfNotNull(kept)

        /** What the intent pays: the total less [kept]. */
        val sent: Long get() = parts.sum()

        val isEven: Boolean get() = shares.all { it == shares.first() }

        /** The part shown as "Each": every part when [isEven], the smallest otherwise. */
        val smallest: Long get() = shares.min()
    }

    data object NoRecipients : GroupSendPlan

    /** More than [GroupSendSplit.MAX_RECIPIENTS] shares, the sender's included, were selected. */
    data object TooManyRecipients : GroupSendPlan

    /** The total is under [GroupSendSplit.MINIMUM_PART_UNITS] per share, the sender's included. */
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
 *
 * A sender who includes themselves takes the last share, so they never take a leftover unit, and
 * keep it: the parts paid are the shares before it. The cap and the minimum count their share.
 */
object GroupSendSplit {
    const val MAX_RECIPIENTS = 10
    const val MINIMUM_PART_UNITS = 1L

    /**
     * @param recipients the members paid, not counting the sender.
     * @param includesSender whether the sender takes a share of [totalUnits] and keeps it.
     */
    fun split(totalUnits: Long, recipients: Int, includesSender: Boolean = false): GroupSendPlan {
        if (recipients <= 0) return GroupSendPlan.NoRecipients
        val shares = if (includesSender) recipients + 1 else recipients
        if (shares > MAX_RECIPIENTS) return GroupSendPlan.TooManyRecipients
        val base = totalUnits / shares
        if (base < MINIMUM_PART_UNITS) return GroupSendPlan.PartBelowMinimum
        val leftover = totalUnits % shares
        return GroupSendPlan.Pay(
            parts = List(recipients) { i -> if (i < leftover) base + 1 else base },
            // The last share: the leftover never reaches it, since leftover < shares.
            kept = base.takeIf { includesSender },
        )
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
