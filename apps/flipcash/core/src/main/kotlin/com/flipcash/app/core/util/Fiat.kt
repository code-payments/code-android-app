package com.flipcash.app.core.util

import com.getcode.opencode.model.financial.Fiat

/**
 * The amount formatted to fit a fixed-width control, capped at [maxDigits] digits: anything under
 * the first scale is formatted as usual, and larger amounts are scaled to K/M/B/T with only as many
 * decimals as the cap leaves room for — trailing zeros dropped.
 *
 * The cap is what keeps a localized amount inside its button: a $20 tip stays "$20", but the same
 * tip in rupiah is Rp332,000, which shows as "Rp332K" rather than overflowing.
 */
fun Fiat.abbreviated(maxDigits: Int = 3): String {
    val abbreviation = decimalValue.abbreviatedIn(maxDigits)
        ?: return formatted(rule = Fiat.FormattingRule.Truncated)

    return Fiat(fiat = abbreviation.value, currencyCode = currencyCode)
        .formatted(rule = Fiat.FormattingRule.Length(abbreviation.decimals)) + abbreviation.suffix
}
