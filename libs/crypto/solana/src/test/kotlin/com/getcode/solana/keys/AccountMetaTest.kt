package com.getcode.solana.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountMetaTest {

    private fun testKey(index: Int): PublicKey {
        return PublicKey(ByteArray(32) { index.toByte() }.toList())
    }

    // Factory method tests

    @Test
    fun payerSetsCorrectFlags() {
        val meta = AccountMeta.payer(testKey(1))
        assertTrue(meta.isSigner)
        assertTrue(meta.isWritable)
        assertTrue(meta.isPayer)
        assertFalse(meta.isProgram)
    }

    @Test
    fun writableDefaultSetsCorrectFlags() {
        val meta = AccountMeta.writable(testKey(2))
        assertFalse(meta.isSigner)
        assertTrue(meta.isWritable)
        assertFalse(meta.isPayer)
        assertFalse(meta.isProgram)
    }

    @Test
    fun writableWithSignerSetsCorrectFlags() {
        val meta = AccountMeta.writable(testKey(2), signer = true)
        assertTrue(meta.isSigner)
        assertTrue(meta.isWritable)
        assertFalse(meta.isPayer)
        assertFalse(meta.isProgram)
    }

    @Test
    fun readonlyDefaultSetsCorrectFlags() {
        val meta = AccountMeta.readonly(testKey(3))
        assertFalse(meta.isSigner)
        assertFalse(meta.isWritable)
        assertFalse(meta.isPayer)
        assertFalse(meta.isProgram)
    }

    @Test
    fun readonlyWithSignerSetsCorrectFlags() {
        val meta = AccountMeta.readonly(testKey(3), signer = true)
        assertTrue(meta.isSigner)
        assertFalse(meta.isWritable)
        assertFalse(meta.isPayer)
        assertFalse(meta.isProgram)
    }

    @Test
    fun programSetsCorrectFlags() {
        val meta = AccountMeta.program(testKey(4))
        assertFalse(meta.isSigner)
        assertFalse(meta.isWritable)
        assertFalse(meta.isPayer)
        assertTrue(meta.isProgram)
    }

    // Sorting tests

    @Test
    fun payerSortsBeforeSigner() {
        val payer = AccountMeta.payer(testKey(1))
        val signer = AccountMeta.readonly(testKey(2), signer = true)
        assertTrue(payer < signer)
    }

    @Test
    fun signerSortsBeforeWritable() {
        val signer = AccountMeta.readonly(testKey(1), signer = true)
        val writable = AccountMeta.writable(testKey(2))
        assertTrue(signer < writable)
    }

    @Test
    fun writableSortsBeforeReadonly() {
        val writable = AccountMeta.writable(testKey(1))
        val readonly = AccountMeta.readonly(testKey(2))
        assertTrue(writable < readonly)
    }

    @Test
    fun readonlySortsBeforeProgram() {
        val readonly = AccountMeta.readonly(testKey(1))
        val program = AccountMeta.program(testKey(2))
        assertTrue(readonly < program)
    }

    @Test
    fun fullSortOrder() {
        val program = AccountMeta.program(testKey(5))
        val readonly = AccountMeta.readonly(testKey(4))
        val writable = AccountMeta.writable(testKey(3))
        val signer = AccountMeta.readonly(testKey(2), signer = true)
        val payer = AccountMeta.payer(testKey(1))

        val sorted = listOf(program, readonly, writable, signer, payer).sorted()

        assertEquals(payer, sorted[0])
        assertEquals(signer, sorted[1])
        assertEquals(writable, sorted[2])
        assertEquals(readonly, sorted[3])
        assertEquals(program, sorted[4])
    }

    // filterUniqueAccounts tests

    @Test
    fun filterUniqueAccountsDeduplicates() {
        val key = testKey(1)
        val meta1 = AccountMeta.readonly(key)
        val meta2 = AccountMeta.readonly(key)
        val result = listOf(meta1, meta2).filterUniqueAccounts()
        assertEquals(1, result.size)
    }

    @Test
    fun filterUniqueAccountsPromotesPermissions() {
        val key = testKey(1)
        val readonly = AccountMeta.readonly(key)
        val writable = AccountMeta.writable(key)
        val result = listOf(readonly, writable).filterUniqueAccounts()

        assertEquals(1, result.size)
        assertTrue(result[0].isWritable, "Should promote to writable")
    }

    @Test
    fun filterUniqueAccountsPromotesSigner() {
        val key = testKey(1)
        val nonSigner = AccountMeta.writable(key, signer = false)
        val signer = AccountMeta.readonly(key, signer = true)
        val result = listOf(nonSigner, signer).filterUniqueAccounts()

        assertEquals(1, result.size)
        assertTrue(result[0].isSigner, "Should promote to signer")
        assertTrue(result[0].isWritable, "Should retain writable from first entry")
    }

    @Test
    fun filterUniqueAccountsPreservesDistinctKeys() {
        val meta1 = AccountMeta.readonly(testKey(1))
        val meta2 = AccountMeta.writable(testKey(2))
        val result = listOf(meta1, meta2).filterUniqueAccounts()
        assertEquals(2, result.size)
    }

    // compareLexicographically tests

    @Test
    fun compareLexicographicallyEqual() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3)
        assertEquals(0, AccountMeta.compareLexicographically(a, b))
    }

    @Test
    fun compareLexicographicallyLessThan() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 4)
        assertTrue(AccountMeta.compareLexicographically(a, b) < 0)
    }

    @Test
    fun compareLexicographicallyGreaterThan() {
        val a = byteArrayOf(1, 3, 3)
        val b = byteArrayOf(1, 2, 3)
        assertTrue(AccountMeta.compareLexicographically(a, b) > 0)
    }

    @Test
    fun compareLexicographicallyShorterArray() {
        val a = byteArrayOf(1, 2)
        val b = byteArrayOf(1, 2, 3)
        assertTrue(AccountMeta.compareLexicographically(a, b) < 0)
    }
}
