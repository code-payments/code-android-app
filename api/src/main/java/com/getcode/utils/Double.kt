package com.getcode.utils


fun Double.toByteArray(): ByteArray {
    val longBits = java.lang.Double.doubleToLongBits(this)
    val byteArray = ByteArray(8)

    for (i in 0 until 8) {
        byteArray[i] = (longBits shr (8 * (7 - i))).toByte()
    }

    return byteArray
}