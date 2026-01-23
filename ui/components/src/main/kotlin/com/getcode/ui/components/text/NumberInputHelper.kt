package com.getcode.ui.components.text

import java.text.NumberFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.min


class NumberInputHelper {
    private var amountText = ""
    var amount: Double = 0.0
    var maxLength: Int = 9

    var fractionUnits: Int = 2

    private val isDecimalAllowed: Boolean
        get() = fractionUnits > 0

    fun reset() {
        amount = 0.0
        amountText = "0"
    }

    fun onNumber(v: Int) {
        val s = amountText.split(DECIMAL_SEPARATOR)
        if (s.size == 1 && s[0].length >= maxLength) {
            return
        }
        if (s.size > 1 && s[1].length >= fractionUnits) {
            return
        }

        if (amountText == CONST_ZERO) {
            amountText = ""
        }

        amountText += v

        applyValue()
    }

    fun onDot() {
        if (!isDecimalAllowed) {
            return
        }

        if (!amountText.contains(DECIMAL_SEPARATOR)) {
            amountText += DECIMAL_SEPARATOR
        }
    }

    fun onBackspace() {
        val isNotEmpty = amountText.isNotEmpty() && amountText != CONST_ZERO
        if (isNotEmpty) {
            amountText =
                amountText.substring(0, amountText.length - 1) //getValueWithoutTrailingPeriod
            if (amountText.isEmpty()) amountText = CONST_ZERO
        }
        applyValue()
    }

    fun getFormattedString(): String {
        return formatAmount(amount)
    }

    fun getFormattedStringForAnimation(includeCommas: Boolean = true): AmountAnimatedData {
        return formatAmountForAnimation(amount, includeCommas)
    }

    private fun getValueWithoutTrailingPeriod(v: String): String {
        if (v.isNotBlank() && v.last() == DECIMAL_SEPARATOR) return v.replace(DECIMAL_SEPARATOR.toString(), "")
        return v
    }

    private fun applyValue() {
        amount = amountText.toLocaleAwareDoubleOrNull() ?: 0.0
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1 == 0.0 && !amountText.contains(DECIMAL_SEPARATOR)) {
            String.format("%,.0f", amount)
        } else {
            String.format("%,.${fractionUnits}f", amount)
        }
    }

    private fun formatAmountForAnimation(amount: Double, includeCommas: Boolean = true): AmountAnimatedData {
        val isContainsDot = amountText.contains(DECIMAL_SEPARATOR)
        val decimalLength = min(
            amountText.split(DECIMAL_SEPARATOR).getOrNull(1)?.length ?: 0, fractionUnits
        )
        return when (decimalLength) {
            0 -> String.format("%,.0f", amount).let { if (isContainsDot) "$it$DECIMAL_SEPARATOR" else it }
            else ->  String.format("%,.${decimalLength}f", amount)
        }.let {
            val amountWithoutCommas = it.replace(GROUPING_SEPARATOR.toString(), "")
            val lengthWithoutCommas = amountWithoutCommas.length
            val commaVisibility = mutableListOf(*Array(lengthWithoutCommas) { false })
            if (includeCommas) {
                var offset = 0
                it.forEachIndexed { index, c ->
                    val isComma = c == GROUPING_SEPARATOR
                    commaVisibility[index - offset] = isComma || commaVisibility[index - offset]
                    if (isComma) offset++
                }
            }

            AmountAnimatedData(amountWithoutCommas, commaVisibility)
        }
    }

    companion object {
        const val CONST_ZERO = "0"
        val DECIMAL_SEPARATOR: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator
        val GROUPING_SEPARATOR: Char get() = DecimalFormatSymbols.getInstance().groupingSeparator
    }

    data class AmountAnimatedData(
        internal val amountText: String = "0",
        val commaVisibility: List<Boolean> = listOf()
    ) {
        fun isEmpty(): Boolean = amountText.isEmpty()
        val amount: Double get() = amountText.toLocaleAwareDoubleOrNull() ?: 0.0
    }
}

private fun String.toLocaleAwareDoubleOrNull(): Double? {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    // Use locale-aware NumberFormat for parsing to correctly handle decimal separators.
    // We also need to handle the case where the input is just the separator (e.g., "," or ".")
    // or ends with it, which parse() would fail on.
    val sanitizedText = if (endsWith(separator)) this + "0" else this
    return runCatching { NumberFormat.getInstance().parse(sanitizedText)?.toDouble() }.getOrNull()
}