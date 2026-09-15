package com.getcode.solana.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountMetaTest {

    private fun key(byte: Int) = PublicKey(ByteArray(32) { byte.toByte() }.toList())

    // --- Factory methods ---

    @Test
    fun payerIsSigner() {
        val meta = AccountMeta.payer(key(1))
        assertTrue(meta.isSigner)
    }

    @Test
    fun payerIsWritable() {
        val meta = AccountMeta.payer(key(1))
        assertTrue(meta.isWritable)
    }

    @Test
    fun payerIsPayer() {
        val meta = AccountMeta.payer(key(1))
        assertTrue(meta.isPayer)
    }

    @Test
    fun payerIsNotProgram() {
        val meta = AccountMeta.payer(key(1))
        assertFalse(meta.isProgram)
    }

    @Test
    fun writableIsWritable() {
        val meta = AccountMeta.writable(key(2))
        assertTrue(meta.isWritable)
        assertFalse(meta.isSigner)
    }

    @Test
    fun writableWithSignerIsSigner() {
        val meta = AccountMeta.writable(key(2), signer = true)
        assertTrue(meta.isWritable)
        assertTrue(meta.isSigner)
    }

    @Test
    fun readonlyIsNotWritable() {
        val meta = AccountMeta.readonly(key(3))
        assertFalse(meta.isWritable)
        assertFalse(meta.isSigner)
    }

    @Test
    fun readonlyWithSignerIsSigner() {
        val meta = AccountMeta.readonly(key(3), signer = true)
        assertFalse(meta.isWritable)
        assertTrue(meta.isSigner)
    }

    @Test
    fun programIsProgram() {
        val meta = AccountMeta.program(key(4))
        assertTrue(meta.isProgram)
        assertFalse(meta.isSigner)
        assertFalse(meta.isWritable)
        assertFalse(meta.isPayer)
    }

    // --- Ordering (compareTo) ---

    @Test
    fun payerSortsBeforeNonPayer() {
        val payer = AccountMeta.payer(key(2))
        val writable = AccountMeta.writable(key(1))
        assertTrue(payer < writable)
    }

    @Test
    fun nonProgramSortsBeforeProgram() {
        val writable = AccountMeta.writable(key(2))
        val program = AccountMeta.program(key(1))
        assertTrue(writable < program)
    }

    @Test
    fun signerSortsBeforeNonSigner() {
        val signer = AccountMeta.writable(key(2), signer = true)
        val nonSigner = AccountMeta.writable(key(1))
        assertTrue(signer < nonSigner)
    }

    @Test
    fun writableSortsBeforeReadonly() {
        val writable = AccountMeta.writable(key(2))
        val readonly = AccountMeta.readonly(key(1))
        assertTrue(writable < readonly)
    }

    @Test
    fun equalFlagsSortByLexicographicKey() {
        val lower = AccountMeta.writable(key(1))
        val higher = AccountMeta.writable(key(2))
        assertTrue(lower < higher)
    }

    @Test
    fun sortedListProducesExpectedOrder() {
        val payer = AccountMeta.payer(key(1))
        val signer = AccountMeta.writable(key(2), signer = true)
        val writable = AccountMeta.writable(key(3))
        val readonly = AccountMeta.readonly(key(4))
        val program = AccountMeta.program(key(5))

        val sorted = listOf(program, readonly, writable, signer, payer).sorted()
        assertEquals(listOf(payer, signer, writable, readonly, program), sorted)
    }

    // --- compareLexicographically ---

    @Test
    fun compareLexicographicallyEqualArraysReturnZero() {
        val a = byteArrayOf(1, 2, 3)
        assertEquals(0, AccountMeta.compareLexicographically(a, a.copyOf()))
    }

    @Test
    fun compareLexicographicallyFirstByteDeterminesOrder() {
        val a = byteArrayOf(1, 0)
        val b = byteArrayOf(2, 0)
        assertTrue(AccountMeta.compareLexicographically(a, b) < 0)
        assertTrue(AccountMeta.compareLexicographically(b, a) > 0)
    }

    @Test
    fun compareLexicographicallyTreatsAsUnsigned() {
        val low = byteArrayOf(0x00)
        val high = byteArrayOf(0xFF.toByte())
        assertTrue(AccountMeta.compareLexicographically(low, high) < 0)
    }

    @Test
    fun compareLexicographicallyShorterIsSmaller() {
        val short = byteArrayOf(1, 2)
        val long = byteArrayOf(1, 2, 3)
        assertTrue(AccountMeta.compareLexicographically(short, long) < 0)
    }

    // --- filterUniqueAccounts ---

    @Test
    fun filterUniqueAccountsDeduplicatesByPublicKey() {
        val list = listOf(
            AccountMeta.readonly(key(1)),
            AccountMeta.readonly(key(1)),
        )
        assertEquals(1, list.filterUniqueAccounts().size)
    }

    @Test
    fun filterUniqueAccountsPromotesToWritable() {
        val list = listOf(
            AccountMeta.readonly(key(1)),
            AccountMeta.writable(key(1)),
        )
        val unique = list.filterUniqueAccounts()
        assertEquals(1, unique.size)
        assertTrue(unique[0].isWritable)
    }

    @Test
    fun filterUniqueAccountsPromotesToSigner() {
        val list = listOf(
            AccountMeta.writable(key(1)),
            AccountMeta.writable(key(1), signer = true),
        )
        val unique = list.filterUniqueAccounts()
        assertEquals(1, unique.size)
        assertTrue(unique[0].isSigner)
    }

    @Test
    fun filterUniqueAccountsPromotesToPayer() {
        val list = listOf(
            AccountMeta.readonly(key(1)),
            AccountMeta.payer(key(1)),
        )
        val unique = list.filterUniqueAccounts()
        assertEquals(1, unique.size)
        assertTrue(unique[0].isPayer)
    }

    @Test
    fun filterUniqueAccountsPreservesDistinctKeys() {
        val list = listOf(
            AccountMeta.readonly(key(1)),
            AccountMeta.writable(key(2)),
            AccountMeta.program(key(3)),
        )
        assertEquals(3, list.filterUniqueAccounts().size)
    }

    @Test
    fun filterUniqueAccountsEmptyList() {
        assertEquals(0, emptyList<AccountMeta>().filterUniqueAccounts().size)
    }

    // --- description ---

    @Test
    fun descriptionShowsPayerFlags() {
        val meta = AccountMeta.payer(key(0))
        assertTrue(meta.description.startsWith("[ps"))
    }

    @Test
    fun descriptionShowsProgramFlags() {
        val meta = AccountMeta.program(key(0))
        assertTrue(meta.description.startsWith("[--"))
    }
}
