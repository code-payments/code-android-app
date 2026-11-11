package com.getcode.opencode.internal.format

import java.util.Currency

/** Android backs `java.util.Currency` with the device's ICU, so phones keep reading it from the OS. */
internal actual fun isoFractionDigits(code: String): Int = Currency.getInstance(code).defaultFractionDigits
