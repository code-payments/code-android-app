package com.flipcash.app.tokens

import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat

/**
 * How a collection payment's conversion fee reads, from the fee in micros. Fees are added up on
 * micros and rounded half-up to the minor unit for display only, so a fee of exactly half a cent
 * shows as "~$0.01".
 */
enum class ConversionFeeKind {
    /** Nothing converts: no fee line at all. */
    None,

    /** Something converts, at no fee: "no conversion fees" in the modal, nothing in the details. */
    Zero,

    /** Above 0 and under half the minor unit: "<$0.01". */
    UnderMinor,

    /** A whole number of minor units: "$0.04". */
    Exact,

    /** Anything else, rounded half-up: "~$0.02". */
    Approx,
}

fun conversionFeeKind(feeMicros: Long, conversion: Boolean, minorDigits: Int): ConversionFeeKind {
    val unit = minorUnitMicros(minorDigits)
    return when {
        !conversion -> ConversionFeeKind.None
        feeMicros == 0L -> ConversionFeeKind.Zero
        2 * feeMicros < unit -> ConversionFeeKind.UnderMinor
        feeMicros % unit == 0L -> ConversionFeeKind.Exact
        else -> ConversionFeeKind.Approx
    }
}

/** [micros] in whole minor units, rounded half-up: 9_505_000 is 951 cents. */
fun roundedMinorUnits(micros: Long, minorDigits: Int): Long =
    divideHalfUp(micros, minorUnitMicros(minorDigits))

/**
 * The strings a collection payment draws from a [CollectionFundingPlan]: the payment modal's short
 * line, add button and fee line, and the Payment Details rows. One formatter for every fee and
 * amount so they round the same way; `CollectionFundingVectorsTest` holds it to the vectors'
 * `display`, `fee_display` and `amount_display`.
 *
 * Every amount is rounded half-up to the minor unit in [Long] before [Fiat.formatted] renders it,
 * since rounding the `Double` would turn $9.505 into $9.50. The short line is the one exception: it
 * rounds the shortfall up, so it never reads less than is missing.
 *
 * @param topUpSourceName how the top-up is paid, as its sublabel names it: "Google Pay".
 * @param strings the text around each amount, from the app's string resources.
 */
class CollectionFundingFormatter(
    private val currency: CurrencyCode,
    private val minorDigits: Int,
    private val topUpSourceName: String,
    private val strings: CollectionFundingStrings,
) {

    /** [micros] rounded half-up to the minor unit: "$9.51" for 9_505_000. */
    fun amount(micros: Long): String = minorUnits(roundedMinorUnits(micros, minorDigits))

    /** The modal's fee line, without the info affordance after it, or null when there is none. */
    fun modalFee(feeMicros: Long, conversion: Boolean): String? =
        when (conversionFeeKind(feeMicros, conversion, minorDigits)) {
            ConversionFeeKind.None -> null
            ConversionFeeKind.Zero -> strings.noConversionFees
            else -> strings.plusConversionFees(feeText(feeMicros))
        }

    /** The fee line under a sold currency in Payment Details, or null when it carries none. */
    fun feeSublabel(feeMicros: Long, conversion: Boolean): String? =
        when (conversionFeeKind(feeMicros, conversion, minorDigits)) {
            ConversionFeeKind.None, ConversionFeeKind.Zero -> null
            else -> strings.conversionFee(feeText(feeMicros))
        }

    /**
     * Everything the payment draws for [plan].
     *
     * @param amountMicros the collection's fixed amount, the one [plan] was made for.
     * @param nameOf a currency's display name from its id, held or not.
     */
    fun display(
        plan: CollectionFundingPlan,
        amountMicros: Long,
        nameOf: (String) -> String,
    ): CollectionFundingDisplay {
        val amount = amount(amountMicros)
        val short = plan.topUpMicros > 0
        val unit = minorUnitMicros(minorDigits)
        return CollectionFundingDisplay(
            shortBy = if (short) strings.shortBy(minorUnits(ceilDiv(plan.shortfallMicros, unit))) else null,
            addButton = if (short) strings.addAndPay(topUp = amount(plan.topUpMicros), amount = amount) else null,
            topUp = if (short) amount(plan.topUpMicros) else null,
            modalFee = modalFee(plan.totalFeeMicros, conversion = plan.legs.any { it.conversion }),
            details = PaymentDetailsDisplay(
                pay = PaymentDetailsRow(nameOf(plan.payingCurrency), amount),
                use = plan.paymentDetailsOrder().map { leg -> PaymentDetailsRow(nameOf(leg.source), amount(leg.soldMicros), sublabels(leg)) },
                total = PaymentDetailsRow(strings.total, amount(plan.legs.sumOf { it.soldMicros })),
            ),
        )
    }

    /** Dollars name where a top-up comes in, the balance first; a sold community currency its fee. */
    private fun sublabels(leg: FundingLeg): List<String> {
        val split = leg.dollarsSplit
        if (split != null) {
            if (split.fromTopUpMicros == 0L) return emptyList()
            return buildList {
                if (split.fromBalanceMicros > 0) add(strings.fromBalance(amount(split.fromBalanceMicros)))
                add(strings.fromTopUp(amount(split.fromTopUpMicros), topUpSourceName))
            }
        }
        return listOfNotNull(feeSublabel(leg.feeMicros, leg.conversion))
    }

    private fun feeText(feeMicros: Long): String = when (conversionFeeKind(feeMicros, true, minorDigits)) {
        ConversionFeeKind.UnderMinor -> strings.feeUnderMinor(minorUnits(1))
        ConversionFeeKind.Approx -> strings.feeApprox(amount(feeMicros))
        else -> amount(feeMicros)
    }

    private fun minorUnits(units: Long): String =
        Fiat(quarks = units * minorUnitMicros(minorDigits), currencyCode = currency)
            .formatted(Fiat.FormattingRule.Length(minorDigits))
}

/**
 * The text a [CollectionFundingFormatter] puts around its amounts. Android reads each from a string
 * resource, its format arguments in the order the vectors' English text has them.
 */
interface CollectionFundingStrings {
    /** "no conversion fees". */
    val noConversionFees: String

    /** "plus ~$0.02 of conversion fees", from the fee text. */
    fun plusConversionFees(fee: String): String

    /** "~$0.02 conversion fee", from the fee text. */
    fun conversionFee(fee: String): String

    /** "<$0.01", from one minor unit. */
    fun feeUnderMinor(minorUnit: String): String

    /** "~$0.02", from the fee rounded half-up. */
    fun feeApprox(fee: String): String

    /** "You’re $2.04 Short", from the shortfall rounded up. */
    fun shortBy(shortfall: String): String

    /** "Add $5.00 and Pay $11.00". */
    fun addAndPay(topUp: String, amount: String): String

    /** "Total". */
    val total: String

    /** "$6.00 from balance". */
    fun fromBalance(amount: String): String

    /** "$5.00 from Google Pay". */
    fun fromTopUp(amount: String, source: String): String
}

/**
 * The legs in Payment Details' "You’ll Use" order: the paying currency, then Dollars, then the
 * rest by amount sold, largest first. [CollectionFundingFormatter.display] lists its rows in this
 * order, so a caller can pair each row with its currency.
 */
fun CollectionFundingPlan.paymentDetailsOrder(): List<FundingLeg> {
    val (paying, others) = legs.partition { it.source == payingCurrency }
    val (dollars, rest) = others.partition { it.dollarsSplit != null }
    return paying + dollars + rest.sortedWith(compareByDescending<FundingLeg> { it.soldMicros }.thenBy { it.source })
}

data class CollectionFundingDisplay(
    /** "You’re $2.04 Short", when the payment needs a top-up. */
    val shortBy: String?,
    /** "Add $5.00 and Pay $11.00", when the payment needs a top-up. */
    val addButton: String?,
    /** The top-up the Google Pay sheet charges, when there is one. */
    val topUp: String?,
    val modalFee: String?,
    val details: PaymentDetailsDisplay,
)

/** The Payment Details sheet: "You’ll Pay", "You’ll Use", then the total under a divider. */
data class PaymentDetailsDisplay(
    val pay: PaymentDetailsRow,
    val use: List<PaymentDetailsRow>,
    val total: PaymentDetailsRow,
)

data class PaymentDetailsRow(
    val name: String,
    val amount: String,
    val sublabels: List<String> = emptyList(),
)
