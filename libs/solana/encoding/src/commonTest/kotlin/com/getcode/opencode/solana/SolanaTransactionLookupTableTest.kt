package com.getcode.opencode.solana

import com.getcode.opencode.model.transactions.AddressLookupTable
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

class SolanaTransactionLookupTableTest {

    // Seed lives in byte 0 (rest zero), so lexicographic ordering tracks the
    // seed value — enough to make the two tables' accounts sort out of table
    // order.
    private fun publicKey(seed: Int): PublicKey {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return PublicKey(bytes.toList())
    }

    private fun hash(seed: Int): Hash {
        val bytes = ByteArray(32) { if (it == 0) seed.toByte() else 0 }
        return Hash(bytes.toList())
    }

    @Test
    fun `multi-table lookups compile instruction indexes in table-grouped order`() {
        // Loaded accounts resolve on-chain grouped BY TABLE (every table's
        // writables, then every table's readonlys) — not in global sort order.
        // Arrange keys so the two orderings differ: the second table's accounts
        // sort lexicographically BEFORE the first table's.
        val payer = publicKey(1)
        val program = publicKey(2)
        val writableA = publicKey(40) // in table A (sorts after writableB)
        val writableB = publicKey(30) // in table B
        val readonlyA = publicKey(41) // in table A (sorts after readonlyB)
        val readonlyB = publicKey(31) // in table B

        val tableA = AddressLookupTable(publicKey(10), listOf(writableA, readonlyA))
        val tableB = AddressLookupTable(publicKey(11), listOf(writableB, readonlyB))

        val instruction = Instruction(
            program = program,
            accounts = listOf(
                AccountMeta.writable(writableA),
                AccountMeta.writable(writableB),
                AccountMeta.readonly(readonlyA),
                AccountMeta.readonly(readonlyB),
            ),
            data = listOf(7.toByte()),
        )

        val transaction = SolanaTransaction.newV0Instance(
            payer = payer,
            recentBlockhash = hash(9),
            addressLookupTables = listOf(tableA, tableB),
            instructions = listOf(instruction),
        )

        val message = (transaction.message as Message.VersionedV0).message

        // Static: payer(0), program(1). Loaded: tableA.writable(2),
        // tableB.writable(3), tableA.readonly(4), tableB.readonly(5).
        assertEquals(listOf(payer, program), message.staticAccountKeys)
        assertEquals(
            listOf<Byte>(2, 3, 4, 5),
            message.instructions[0].accountIndexes,
        )

        assertEquals(2, message.addressLookupTables.size)
        assertEquals(tableA.publicKey, message.addressLookupTables[0].publicKey)
        assertEquals(listOf<Byte>(0), message.addressLookupTables[0].writableIndexes)
        assertEquals(listOf<Byte>(1), message.addressLookupTables[0].readonlyIndexes)
        assertEquals(tableB.publicKey, message.addressLookupTables[1].publicKey)
        assertEquals(listOf<Byte>(0), message.addressLookupTables[1].writableIndexes)
        assertEquals(listOf<Byte>(1), message.addressLookupTables[1].readonlyIndexes)
    }
}
