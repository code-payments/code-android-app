package com.getcode.opencode.internal.format

/**
 * The decimal places Android reports (`java.util.Currency.getDefaultFractionDigits`, which Android
 * backs with ICU) for every [com.getcode.opencode.model.financial.CurrencyCode], for the targets with
 * no Android runtime. Captured on Android 16 (API 36) on the flipcash_blockstore AVD. The host JVM's JDK table differs on
 * 21 codes; see HostJdkDeviationTest.
 */
internal object AndroidFractionDigits {
    private val notTwo: Map<String, Int> = mapOf(
        "AFN" to 0, "ALL" to 0, "BHD" to 3, "BIF" to 0, "BYR" to 0, "CLF" to 4, "CLP" to 0,
        "COP" to 0, "DJF" to 0, "GNF" to 0, "IDR" to 0, "IQD" to 0, "IRR" to 0, "ISK" to 0,
        "JOD" to 3, "JPY" to 0, "KMF" to 0, "KPW" to 0, "KRW" to 0, "KWD" to 3, "LAK" to 0,
        "LBP" to 0, "LYD" to 3, "MGA" to 0, "MMK" to 0, "MRO" to 0, "MRU" to 0, "OMR" to 3,
        "PYG" to 0, "RSD" to 0, "RWF" to 0, "SLL" to 0, "SOS" to 0, "STD" to 0, "SYP" to 0,
        "TND" to 3, "TZS" to 0, "UGX" to 0, "UYU" to 0, "VND" to 0, "VUV" to 0, "XAF" to 0,
        "XOF" to 0, "XPF" to 0, "YER" to 0, "ZMK" to 0,
    )

    fun of(code: String): Int = notTwo[code] ?: 2
}
