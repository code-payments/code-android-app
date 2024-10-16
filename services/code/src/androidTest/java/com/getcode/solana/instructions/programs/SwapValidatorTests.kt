package com.getcode.solana.instructions.programs

import com.getcode.mocks.SolanaTransaction
import com.getcode.solana.keys.base58
import com.getcode.utils.DataSlice.byteToUnsignedInt
import org.junit.Assert
import org.junit.Test

class SwapValidatorTests {
    @Test
    fun testDecodePreSwap() {
        val (transaction, _) = SolanaTransaction.mockSwapValidatorTransaction()
        val rawInstruction = transaction.message.instructions[2]
        val instruction = SwapValidatorProgram_PreSwap.newInstance(rawInstruction)

        Assert.assertEquals(
            "4Zk9E4HaVBJKnukv2nV8aZQ1ZhqJCs74GaTyXBmPXBN6",
            instruction.preSwapState.base58(),
        )
        Assert.assertEquals(
            "6SLCvHRtnB1UJJN7RmHKq6aJ6Ugo5aQEswQ9f9wgybxy",
            instruction.user.base58()
        )
        Assert.assertEquals(
            "5nNBW1KhzHVbR4NMPLYPRYj3UN5vgiw5GrtpdK6eGoce",
            instruction.source.base58()
        )
        Assert.assertEquals(
            "9Rgx4kjnYZBbeXXgbbYLT2FfgzrNHFUShDtp8dpHHjd2",
            instruction.destination.base58(),
        )
        Assert.assertEquals(
            "2uZYLABYpqCAqE2PHa1nzpVRpy3aB8fUv293y6MQxm1Z",
            instruction.nonce.base58()
        )
        Assert.assertEquals(
            "swapBMF2EzkHSn9NDwaSFWMtGC7ZsgzApQv9NSkeUeU",
            instruction.payer.base58()
        )
        Assert.assertEquals(12, instruction.remainingAccounts.count())
    }

    @Test
    fun testDecodePostSwap() {
        val (transaction, _) = SolanaTransaction.mockSwapValidatorTransaction()
        val rawTransaction = transaction.message.instructions[4]
        val instruction = SwapValidatorProgram_PostSwap.newInstance(instruction = rawTransaction)

        Assert.assertEquals(254, instruction.stateBump.byteToUnsignedInt())
        Assert.assertEquals(10000, instruction.maxToSend)
        Assert.assertEquals(57277492, instruction.minToReceive)
        Assert.assertEquals(
            "4Zk9E4HaVBJKnukv2nV8aZQ1ZhqJCs74GaTyXBmPXBN6",
            instruction.preSwapState.base58(),
        )
        Assert.assertEquals(
            "5nNBW1KhzHVbR4NMPLYPRYj3UN5vgiw5GrtpdK6eGoce",
            instruction.source.base58()
        )
        Assert.assertEquals(
            "9Rgx4kjnYZBbeXXgbbYLT2FfgzrNHFUShDtp8dpHHjd2", instruction.destination.base58(),
        )
        Assert.assertEquals(
            "swapBMF2EzkHSn9NDwaSFWMtGC7ZsgzApQv9NSkeUeU",
            instruction.payer.base58()
        )
    }

    @Test
    fun testDecodeComputeUnitLimit() {
        val (transaction, _) = SolanaTransaction.mockSwapValidatorTransaction()
        val rawInstruction = transaction.message.instructions[0]
        val instruction =
            ComputeBudgetProgram_SetComputeUnitLimit.newInstance(instruction = rawInstruction)
        Assert.assertEquals(1400000, instruction.limit)
    }

    @Test
    fun testDecodeComputeUnitPrice() {
        val (transaction, _) = SolanaTransaction.mockSwapValidatorTransaction()
        val rawInstruction = transaction.message.instructions[1]
        val instruction =
            ComputeBudgetProgram_SetComputeUnitPrice.newInstance(instruction = rawInstruction)

        Assert.assertEquals(4206, instruction.microLamports)
    }
}