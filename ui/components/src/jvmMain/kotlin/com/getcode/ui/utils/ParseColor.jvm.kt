package com.getcode.ui.utils

internal actual fun parseColorInt(hex: String): Int {
    require(hex.startsWith("#")) { "Unknown color: $hex" }
    val value = hex.substring(1).toLong(16)
    return when (hex.length) {
        7 -> (value or 0xFF000000).toInt()
        9 -> value.toInt()
        else -> throw IllegalArgumentException("Unknown color: $hex")
    }
}
