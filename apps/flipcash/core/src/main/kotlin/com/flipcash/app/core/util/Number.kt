package com.flipcash.app.core.util

fun Number.abbreviated(): String {
    val value = toDouble()
    return when {
        value >= 1_000_000_000_000.0 -> "%.2fT".format(value / 1_000_000_000_000.0).trimTrailingZeros()
        value >= 1_000_000_000.0     -> "%.2fB".format(value / 1_000_000_000.0).trimTrailingZeros()
        value >= 1_000_000.0         -> "%.2fM".format(value / 1_000_000.0).trimTrailingZeros()
        value >= 1_000.0             -> "%.2fK".format(value / 1_000.0).trimTrailingZeros()
        else                         -> value.toLong().toString()
    }
}

private fun String.trimTrailingZeros(): String {
    val suffix = last()
    return dropLast(1).trimEnd('0').trimEnd('.') + suffix
}