package com.getcode.utils.network

internal fun Int.toSignalStrength() = when (this) {
    0 -> SignalStrength.Weak
    1 -> SignalStrength.Poor
    2 -> SignalStrength.Good
    3 -> SignalStrength.Great
    4 -> SignalStrength.Strong
    else -> SignalStrength.Unknown
}