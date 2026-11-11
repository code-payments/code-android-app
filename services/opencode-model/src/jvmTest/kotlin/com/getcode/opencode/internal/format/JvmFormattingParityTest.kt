package com.getcode.opencode.internal.format

import com.getcode.opencode.model.financial.CurrencyCode
import java.util.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmFormattingParityTest {

    private fun jdk(code: String): Result<Int> = runCatching { Currency.getInstance(code).defaultFractionDigits }

    @Test
    fun theTableForTargetsWithoutAJdkMatchesTheJdkForEveryCurrency() {
        val mismatches = CurrencyCode.entries.mapNotNull { code ->
            val expected = jdk(code.name)
            val actual = runCatching { JdkFractionDigits.of(code.name) }
            val same = if (expected.isFailure) actual.exceptionOrNull() is IllegalArgumentException else actual.getOrNull() == expected.getOrNull()
            if (same) null else "${code.name}: jdk=${expected.getOrNull() ?: "throws"} table=${actual.getOrNull() ?: "throws"}"
        }
        assertEquals(emptyList(), mismatches)
    }

    @Test
    fun singleCharacterSymbolsMatchTheCharacterGetTypeFilter() {
        val table = buildCurrencySymbolTable()
        for (code in CurrencyCode.entries) {
            val expected = table[code]
                ?.filter { it.length == 1 && (Character.getType(it[0]) == Character.CURRENCY_SYMBOL.toInt() || it[0] in 'A'..'Z') }
                ?.minByOrNull { it[0].code in 0xFF00..0xFFEF }
            assertEquals(expected, code.singleCharacterCurrencySymbol, code.name)
        }
    }

    @Test
    fun theSymbolTableForTargetsWithoutLocalesMatchesTheJvmsInOrder() {
        val jvm = buildCurrencySymbolTable().mapKeys { it.key.name }.mapValues { it.value.toList() }
        assertEquals(jvm, JdkCurrencySymbols.table)
    }
}
