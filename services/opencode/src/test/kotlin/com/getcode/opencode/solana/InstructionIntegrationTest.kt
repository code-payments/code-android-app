package com.getcode.opencode.solana

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstructionIntegrationTest {

    private fun publicKey(seed: Int): PublicKey {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return PublicKey(bytes.toList())
    }

    // --- CompiledInstruction encode / fromList roundtrip ---

    @Test
    fun compiledInstructionEncodeDecodeRoundtrip() {
        val original = CompiledInstruction(
            programIndex = 3,
            accountIndexes = listOf(0, 1, 2),
            data = listOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        )

        val encoded = original.encode()
        val decoded = CompiledInstruction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(original.programIndex, decoded.programIndex)
        assertEquals(original.accountIndexes, decoded.accountIndexes)
        assertEquals(original.data, decoded.data)
    }

    @Test
    fun compiledInstructionEmptyData() {
        val original = CompiledInstruction(
            programIndex = 0,
            accountIndexes = listOf(1),
            data = emptyList()
        )

        val encoded = original.encode()
        val decoded = CompiledInstruction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(0, decoded.data.size)
    }

    @Test
    fun compiledInstructionEmptyAccounts() {
        val original = CompiledInstruction(
            programIndex = 0,
            accountIndexes = emptyList(),
            data = listOf(0x01)
        )

        val encoded = original.encode()
        val decoded = CompiledInstruction.fromList(encoded)

        assertNotNull(decoded)
        assertEquals(0, decoded.accountIndexes.size)
    }

    @Test
    fun fromListTooShortReturnsNull() {
        assertNull(CompiledInstruction.fromList(emptyList()))
        assertNull(CompiledInstruction.fromList(listOf(0)))
    }

    @Test
    fun byteLengthMatchesEncoded() {
        val instruction = CompiledInstruction(
            programIndex = 2,
            accountIndexes = listOf(0, 1, 3, 4),
            data = listOf(0x01, 0x02, 0x03, 0x04, 0x05)
        )
        assertEquals(instruction.encode().size, instruction.byteLength)
    }

    // --- Instruction compile / decompile roundtrip ---

    @Test
    fun compileDecompileRoundtrip() {
        val program = publicKey(10)
        val acc0 = publicKey(1)
        val acc1 = publicKey(2)

        val instruction = Instruction(
            program = program,
            accounts = listOf(
                AccountMeta.writable(acc0, signer = true),
                AccountMeta.readonly(acc1),
            ),
            data = listOf(0x01, 0x02)
        )

        val messageAccounts = listOf(acc0, acc1, program)
        val compiled = instruction.compile(messageAccounts)

        assertEquals(2.toByte(), compiled.programIndex) // program at index 2
        assertEquals(listOf<Byte>(0, 1), compiled.accountIndexes) // acc0=0, acc1=1

        // Decompile back
        val accountMetas = listOf(
            AccountMeta.writable(acc0, signer = true),
            AccountMeta.readonly(acc1),
            AccountMeta.readonly(program),
        )
        val decompiled = compiled.decompile(accountMetas)

        assertNotNull(decompiled)
        assertEquals(program, decompiled.program)
        assertEquals(2, decompiled.accounts.size)
        assertEquals(acc0, decompiled.accounts[0].publicKey)
        assertEquals(acc1, decompiled.accounts[1].publicKey)
        assertEquals(instruction.data, decompiled.data)
    }

    @Test
    fun decompileWithInsufficientAccountsReturnsNull() {
        val compiled = CompiledInstruction(
            programIndex = 5,
            accountIndexes = listOf(0, 1, 2),
            data = emptyList()
        )
        // Need at least 4 accounts (3 account indexes + 1 program), only providing 3
        val accounts = listOf(
            AccountMeta.readonly(publicKey(1)),
            AccountMeta.readonly(publicKey(2)),
            AccountMeta.readonly(publicKey(3)),
        )
        assertNull(compiled.decompile(accounts))
    }

    // --- Instruction equality ---

    @Test
    fun instructionEquality() {
        val a = Instruction(publicKey(1), listOf(AccountMeta.readonly(publicKey(2))), listOf(0x01))
        val b = Instruction(publicKey(1), listOf(AccountMeta.readonly(publicKey(2))), listOf(0x01))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- Multiple instructions encode/decode ---

    @Test
    fun multipleCompiledInstructionsEncodeDecodeSequentially() {
        val instr1 = CompiledInstruction(0, listOf(1, 2), listOf(0x0A))
        val instr2 = CompiledInstruction(3, listOf(0), listOf(0x0B, 0x0C))

        val encoded1 = instr1.encode()
        val encoded2 = instr2.encode()
        val combined = encoded1 + encoded2

        // Decode first instruction
        val decoded1 = CompiledInstruction.fromList(combined)
        assertNotNull(decoded1)
        assertEquals(instr1.programIndex, decoded1.programIndex)
        assertEquals(instr1.data, decoded1.data)

        // Decode second instruction from remainder
        val remainder = combined.drop(decoded1.byteLength)
        val decoded2 = CompiledInstruction.fromList(remainder)
        assertNotNull(decoded2)
        assertEquals(instr2.programIndex, decoded2.programIndex)
        assertEquals(instr2.data, decoded2.data)
    }
}
