package com.getcode.opencode.internal.solana.programs

import com.getcode.solana.keys.LENGTH_32
import com.getcode.solana.keys.base58
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramAddressesTest {

    @Test
    fun `ComputeBudgetProgram address is correct`() {
        assertEquals(
            "ComputeBudget111111111111111111111111111111",
            ComputeBudgetProgram.address.base58()
        )
    }

    @Test
    fun `AssociatedTokenProgram address is correct`() {
        assertEquals(
            "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL",
            AssociatedTokenProgram.address.base58()
        )
    }

    @Test
    fun `MemoProgram address is correct`() {
        assertEquals(
            "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo",
            MemoProgram.address.base58()
        )
    }

    @Test
    fun `SystemProgram address is all zeros`() {
        val bytes = SystemProgram.address.bytes
        assertEquals(LENGTH_32, bytes.size)
        assertTrue(bytes.all { it == 0.toByte() })
    }

    @Test
    fun `TimelockProgram address is correct`() {
        assertEquals(
            "time2Z2SCnn3qYg3ULKVtdkh8YmZ5jFdKicnA1W2YnJ",
            TimelockProgram.address.base58()
        )
    }

    @Test
    fun `TimelockProgram legacyAddress is correct`() {
        assertEquals(
            "timeDBoQGL52du9K7EtrhkJSqpiFapE9dHrmDVkuZx6",
            TimelockProgram.legacyAddress.base58()
        )
    }

    @Test
    fun `SwapValidatorProgram address is correct`() {
        assertEquals(
            "sWvA66HNNvgamibZe88v3NN5nQwE8tp3KitfViFjukA",
            SwapValidatorProgram.address.base58()
        )
    }

    @Test
    fun `TokenProgram address is correct`() {
        assertEquals(
            "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
            TokenProgram.address.base58()
        )
    }

    @Test
    fun `CurrencyCreatorProgram address is correct`() {
        assertEquals(
            "ccJYP5gjZqcEHaphcxAZvkxCrnTVfYMjyhSYkpQtf8Z",
            CurrencyCreatorProgram.address.base58()
        )
    }

    @Test
    fun `UsdfProgram address is correct`() {
        assertEquals(
            "usdfcP2V1bh1Lz7Y87pxR4zJd3wnVtssJ6GeSHFeZeu",
            UsdfProgram.address.base58()
        )
    }

    @Test
    fun `VirtualMachineProgram address is correct`() {
        assertEquals(
            "vmZ1WUq8SxjBWcaeTCvgJRZbS84R61uniFsQy5YMRTJ",
            VirtualMachineProgram.address.base58()
        )
    }

    // Command enum entry counts

    @Test
    fun `ComputeBudgetProgram Command has 4 entries`() {
        assertEquals(4, ComputeBudgetProgram.Command.entries.size)
    }

    @Test
    fun `TokenProgram Command has 2 entries with correct values`() {
        val entries = TokenProgram.Command.entries
        assertEquals(2, entries.size)
        assertEquals(3.toByte(), TokenProgram.Command.transfer.value)
        assertEquals(9.toByte(), TokenProgram.Command.closeAccount.value)
    }

    @Test
    fun `SystemProgram Command has 12 entries`() {
        assertEquals(12, SystemProgram.Command.entries.size)
    }

    @Test
    fun `CurrencyCreatorProgram Command has 8 entries`() {
        assertEquals(8, CurrencyCreatorProgram.Command.entries.size)
    }

    @Test
    fun `UsdfProgram Command has 2 entries with correct values`() {
        val entries = UsdfProgram.Command.entries
        assertEquals(2, entries.size)
        assertEquals(2.toByte(), UsdfProgram.Command.swap.value)
        assertEquals(3.toByte(), UsdfProgram.Command.transfer.value)
    }
}
