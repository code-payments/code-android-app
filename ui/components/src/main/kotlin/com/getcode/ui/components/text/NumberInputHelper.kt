package com.getcode.ui.components.text

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.*
import kotlin.math.min


class NumberInputHelper {
    private var amountText = ""
    var amount: Double = 0.0
    var maxLength: Int = 9
    var isDecimalAllowed: Boolean = true

    fun reset() {
        amount = 0.0
        amountText = "0"
    }

    fun onNumber(v: Int) {
        val s = amountText.split(DECIMAL_SEPARATOR)
        if (s.size == 1 && s[0].length >= maxLength ||
            s.size > 1 && s[1].length >= 2
        ) {
            return
        } else if (amountText == CONST_ZERO) {
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
        val format: NumberFormat = NumberFormat.getInstance(Locale.getDefault())
        amount = format.parse(amountText)?.toDouble() ?: 0.0
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1 == 0.0 && !amountText.contains(DECIMAL_SEPARATOR)) {
            String.format("%,.0f", amount)
        } else {
            String.format("%,.2f", amount)
        }
    }

    private fun formatAmountForAnimation(amount: Double, includeCommas: Boolean = true): AmountAnimatedData {
        val isContainsDot = amountText.contains(DECIMAL_SEPARATOR)
        val decimalLength = min(
            amountText.split(DECIMAL_SEPARATOR).getOrNull(1)?.length ?: 0, 2
        )
        return when (decimalLength) {
            2 -> String.format("%,.2f", amount)
            1 -> String.format("%,.1f", amount)
            else -> String.format("%,.0f", amount).let { if (isContainsDot) "$it$DECIMAL_SEPARATOR" else it }
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

    data class AmountAnimatedData(val amount: String = "0", val commaVisibility: List<Boolean> = listOf())
}