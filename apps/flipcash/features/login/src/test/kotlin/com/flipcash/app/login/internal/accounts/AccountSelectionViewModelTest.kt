package com.flipcash.app.login.internal.accounts

import com.getcode.crypt.MnemonicPhrase
import org.junit.Test
import kotlin.test.assertEquals

class AccountSelectionViewModelTest {

    // MnemonicPhrase's real constructor is `(kind: Kind, words: List<String>)`; the fixture words
    // below aren't a real 12/24-word phrase, so `Kind.L12` is a placeholder — displayName only
    // reads `words`.
    private fun phrase(vararg words: String) = MnemonicPhrase(MnemonicPhrase.Kind.L12, words.toList())

    @Test
    fun `derives the display name from the first and last word`() {
        val name = AccountSelectionViewModel.displayName(
            phrase("apple", "banana", "cherry", "date", "elder")
        )
        assertEquals("Apple ... Elder", name)
    }

    @Test
    fun `capitalises words that are already capitalised`() {
        val name = AccountSelectionViewModel.displayName(phrase("Apple", "Elder"))
        assertEquals("Apple ... Elder", name)
    }

    @Test
    fun `truncates an owner address in the middle`() {
        val truncated = AccountSelectionViewModel.truncateAddress("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        assertEquals("ABCD...WXYZ", truncated)
    }

    @Test
    fun `leaves a short address alone`() {
        assertEquals("ABCD", AccountSelectionViewModel.truncateAddress("ABCD"))
    }
}
