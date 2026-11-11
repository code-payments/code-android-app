package com.getcode.opencode.internal.format

/**
 * Desktop uses the Android 16 table rather than the host JDK's ISO 4217 table, so its amounts round
 * like a phone's (IQD is 0 places, not 3).
 */
internal actual fun isoFractionDigits(code: String): Int = AndroidFractionDigits.of(code)
