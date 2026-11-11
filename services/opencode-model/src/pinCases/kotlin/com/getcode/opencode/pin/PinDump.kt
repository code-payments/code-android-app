package com.getcode.opencode.pin

import com.getcode.opencode.internal.extensions.fractionDigits
import com.getcode.opencode.internal.format.buildCurrencySymbolTable
import com.getcode.opencode.model.financial.CurrencyCode

/** Every pinned case on one target, in one list, so two targets' outputs can be diffed line by line. */
internal object PinDump {
    fun lines(localeCases: List<String>): List<String> =
        SeamCases.cases().map { "seam|$it" } + AmountPathCases.cases().map { "amount|$it" } + localeCases +
            CurrencyCode.entries.map { "digits|$it = ${runCatching { it.fractionDigits }.getOrElse { "throws" }}" } +
            ParserGrid.cases().map { "parse|$it" } +
            buildCurrencySymbolTable().entries.sortedBy { it.key.name }
                .map { (code, symbols) -> "symbols|$code = ${symbols.joinToString(", ") { "\"${escape(it)}\"" }}" }

    /** Kotlin string-literal body: printable ASCII as is, everything else as `\uXXXX`. */
    fun escape(s: String): String = s.map {
        when {
            it == '"' || it == '\\' || it == '$' -> "\\u" + it.code.toString(16).padStart(4, '0')
            it.code in 0x20..0x7e -> "$it"
            else -> "\\u" + it.code.toString(16).padStart(4, '0')
        }
    }.joinToString("")
}
