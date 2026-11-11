package com.getcode.opencode.internal.format

/**
 * `java.util.Currency.getDefaultFractionDigits` as JDK 21 reports it for every [CurrencyCode], for
 * the targets with no JDK. Kept equal to the JDK by JvmFormattingParityTest on the JVM. ICU (Node's
 * Intl, and Android's runtime) disagrees on 31 codes; the JDK values are the ones the host tests and
 * the amount math were built against.
 */
internal object JdkFractionDigits {
    private val notTwo: Map<String, Int> = mapOf(
        "BHD" to 3, "BIF" to 0, "BYR" to 0, "CLF" to 4, "CLP" to 0, "DJF" to 0, "GNF" to 0,
        "IQD" to 3, "ISK" to 0, "JOD" to 3, "JPY" to 0, "KMF" to 0, "KRW" to 0, "KWD" to 3,
        "LYD" to 3, "OMR" to 3, "PYG" to 0, "RWF" to 0, "TND" to 3, "UGX" to 0, "VND" to 0,
        "VUV" to 0, "XAF" to 0, "XAG" to -1, "XAU" to -1, "XDR" to -1, "XOF" to 0, "XPF" to 0,
    )

    /** Codes the JDK has no currency for: `Currency.getInstance` throws. */
    private val unknown: Set<String> = setOf("BTC", "GGP", "IMP", "JEP")

    fun of(code: String): Int {
        if (code in unknown) throw IllegalArgumentException()
        return notTwo[code] ?: 2
    }
}
