package com.getcode.utils

/**
 * Drawable resource IDs for the currencies that have no country flag. Android maps them to the
 * module's drawables; desktop has no Android resources, so they are null there.
 */
internal expect object CurrencyIcons {
    val kin: Int?
    val xaf: Int?
    val xof: Int?
}
