package com.flipcash.app.currencycreator.internal

import androidx.compose.foundation.text.input.TextFieldState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The client half of `CheckAvailabilityRequest.name`: `max_len 32`, `^[!-~]([ -~]*[!-~])?$`. */
class CurrencyNameTest {

    @Test
    fun `a name at the 32 character limit passes and one over is blocked`() {
        assertTrue(CurrencyName.isValid("a".repeat(32)))
        assertFalse(CurrencyName.isValid("a".repeat(33)))
    }

    @Test
    fun `the limit is measured on the trimmed name`() {
        assertTrue(CurrencyName.isValid("  " + "a".repeat(32) + "  "))
    }

    @Test
    fun `an empty or blank name is blocked`() {
        assertFalse(CurrencyName.isValid(""))
        assertFalse(CurrencyName.isValid("   "))
    }

    @Test
    fun `printable ASCII with inner spaces passes`() {
        assertTrue(CurrencyName.isValid("Bad Boys 2!"))
    }

    @Test
    fun `non ASCII, emoji and control characters are blocked`() {
        assertFalse(CurrencyName.isValid("Caf\u00e9"))
        assertFalse(CurrencyName.isValid("\uD83D\uDE00"))
        assertFalse(CurrencyName.isValid("Bad\tBoys"))
        assertFalse(CurrencyName.isValid("Bad\nBoys"))
    }

    @Test
    fun `the state arms Next only for a valid name`() {
        fun hasName(text: String) = CurrencyCreatorViewModel.State(nameFieldState = TextFieldState(text)).hasName

        assertTrue(hasName("a".repeat(32)))
        assertFalse(hasName("a".repeat(33)))
        assertFalse(hasName("Caf\u00e9"))
    }
}
