package com.getcode.solana

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstructionTest {

    private fun testKey(index: Int): PublicKey {
        return PublicKey(ByteArray(32) { index.toByte() }.toList())
    }

    // Instruction.compile tests

    @Test
    fun compileProducesCorrectProgramIndex() {
        val programKey = testKey(0)
        val accountKey = testKey(1)

        val instruction = Instruction(
            program = programKey,
            accounts = listOf(AccountMeta.readonly(accountKey)),
            data = listOf(0xAA.toByte()),
        )

        val messageAccounts = listOf(programKey, accountKey)
        val compiled = instruction.compile(messageAccounts)

        assertEquals(0.toByte(), compiled.programIndex)
    }

    @Test
    fun compileProducesCorrectAccountIndexes() {
        val programKey = testKey(0)
        val account1 = testKey(1)
        val account2 = testKey(2)

        val instruction = Instruction(
            program = programKey,
            accounts = listOf(
                AccountMeta.readonly(account1),
                AccountMeta.writable(account2),
            ),
            data = listOf(0x01),
        )

        val messageAccounts = listOf(programKey, account1, account2)
        val compiled = instruction.compile(messageAccounts)

        assertEquals(listOf(1.toByte(), 2.toByte()), compiled.accountIndexes)
    }

    @Test
    fun compilePreservesData() {
        val programKey = testKey(0)
        val data = listOf<Byte>(0x01, 0x02, 0x03)

        val instruction = Instruction(
            program = programKey,
            accounts = emptyList(),
            data = data,
        )

        val compiled = instruction.compile(listOf(programKey))
        assertEquals(data, compiled.data)
    }

    // CompiledInstruction encode/fromList roundtrip

    @Test
    fun compiledInstructionEncodeFromListRoundtrip() {
        val compiled = CompiledInstruction(
            programIndex = 3.toByte(),
            accountIndexes = listOf(0.toByte(), 1.toByte(), 2.toByte()),
            data = listOf(0xAA.toByte(), 0xBB.toByte()),
        )

        val encoded = compiled.encode()
        val decoded = CompiledInstruction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(compiled.programIndex, decoded.programIndex)
        assertEquals(compiled.accountIndexes, decoded.accountIndexes)
        assertEquals(compiled.data, decoded.data)
    }

    @Test
    fun compiledInstructionEncodeFromListRoundtripEmptyData() {
        val compiled = CompiledInstruction(
            programIndex = 0.toByte(),
            accountIndexes = listOf(1.toByte()),
            data = emptyList(),
        )

        val encoded = compiled.encode()
        val decoded = CompiledInstruction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(compiled.programIndex, decoded.programIndex)
        assertEquals(compiled.accountIndexes, decoded.accountIndexes)
        assertEquals(compiled.data, decoded.data)
    }

    // decompile tests

    @Test
    fun decompileReversesCompile() {
        val programKey = testKey(0)
        val account1 = testKey(1)
        val account2 = testKey(2)

        val originalInstruction = Instruction(
            program = programKey,
            accounts = listOf(
                AccountMeta.readonly(account1),
                AccountMeta.writable(account2),
            ),
            data = listOf(0x42),
        )

        val messageAccounts = listOf(programKey, account1, account2)
        val accountMetas = listOf(
            AccountMeta.program(programKey),
            AccountMeta.readonly(account1),
            AccountMeta.writable(account2),
        )

        val compiled = originalInstruction.compile(messageAccounts)
        val decompiled = compiled.decompile(accountMetas)

        assertNotNull(decompiled)
        assertEquals(originalInstruction.program, decompiled.program)
        assertEquals(originalInstruction.data, decompiled.data)
        assertEquals(originalInstruction.accounts.size, decompiled.accounts.size)
    }

    @Test
    fun decompileReturnsNullWhenInsufficientAccounts() {
        val compiled = CompiledInstruction(
            programIndex = 0.toByte(),
            accountIndexes = listOf(1.toByte(), 2.toByte()),
            data = listOf(0x01),
        )

        // Only one account, but we need at least 3 (program + 2 account indexes)
        val accounts = listOf(AccountMeta.program(testKey(0)))
        val result = compiled.decompile(accounts)
        assertNull(result)
    }

    // fromList edge cases

    @Test
    fun fromListWithEmptyDataReturnsNull() {
        val result = CompiledInstruction.fromList(emptyList())
        assertNull(result)
    }

    @Test
    fun fromListWithSingleByteReturnsNull() {
        val result = CompiledInstruction.fromList(listOf(0.toByte()))
        assertNull(result)
    }
}
