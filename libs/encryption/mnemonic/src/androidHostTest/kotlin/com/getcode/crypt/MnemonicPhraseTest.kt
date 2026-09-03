package com.getcode.crypt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MnemonicPhraseTest {

    @Test
    fun newInstanceWith12Words() {
        val words = List(12) { "word$it" }
        val phrase = MnemonicPhrase.newInstance(words)
        assertNotNull(phrase)
        assertEquals(MnemonicPhrase.Kind.L12, phrase.kind)
        assertEquals(words, phrase.words)
    }

    @Test
    fun newInstanceWith24Words() {
        val words = List(24) { "word$it" }
        val phrase = MnemonicPhrase.newInstance(words)
        assertNotNull(phrase)
        assertEquals(MnemonicPhrase.Kind.L24, phrase.kind)
        assertEquals(words, phrase.words)
    }

    @Test
    fun newInstanceWithInvalidCountReturnsNull() {
        assertNull(MnemonicPhrase.newInstance(emptyList()))
        assertNull(MnemonicPhrase.newInstance(List(1) { "word" }))
        assertNull(MnemonicPhrase.newInstance(List(11) { "word" }))
        assertNull(MnemonicPhrase.newInstance(List(13) { "word" }))
        assertNull(MnemonicPhrase.newInstance(List(23) { "word" }))
        assertNull(MnemonicPhrase.newInstance(List(25) { "word" }))
    }

    @Test
    fun wordString() {
        val words = listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot",
            "golf", "hotel", "india", "juliet", "kilo", "lima")
        val phrase = MnemonicPhrase.newInstance(words)
        assertNotNull(phrase)
        assertEquals("alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima", phrase.wordString)
    }

    @Test
    fun kindEnum() {
        assertEquals(2, MnemonicPhrase.Kind.entries.size)
        assertNotNull(MnemonicPhrase.Kind.valueOf("L12"))
        assertNotNull(MnemonicPhrase.Kind.valueOf("L24"))
    }
}
