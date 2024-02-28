package com.getcode.solana.builder

import com.getcode.solana.keys.Signature
import com.getcode.mocks.SolanaTransaction
import com.getcode.model.Kin
import com.getcode.model.intents.actions.ActionType.Companion.kreIndex
import com.getcode.network.repository.decodeBase58
import com.getcode.solana.instructions.programs.ComputeBudgetProgram
import com.getcode.solana.instructions.programs.ComputeBudgetProgram_SetComputeUnitLimit
import com.getcode.solana.instructions.programs.ComputeBudgetProgram_SetComputeUnitPrice
import com.getcode.solana.instructions.programs.SwapValidatorProgram_PostSwap
import com.getcode.solana.instructions.programs.SwapValidatorProgram_PreSwap
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.base58
import junit.framework.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionBuilder_Test {

    @Test
    fun testCloseDormantAccountTransaction() {
        val (transaction, _) = SolanaTransaction.mockCloseDormantAccount()

        val authority =
            PublicKey("Ed3GWPEdMiRXDMf7jU46fRwBF7n6ZZFGN3vH1dYAgME2".decodeBase58().toList())
        val destination =
            PublicKey("GEaVZeZ52Jn8xHPy4VKaXsHQ34E6pwfJGuYh8EsYQi6M".decodeBase58().toList())
        val nonce =
            PublicKey("27aoaJKNVtqKXRKQeMdKrtPMqAzcyYH5PGEgQ8x88TMH".decodeBase58().toList())
        val blockhash =
            PublicKey("7mezFVdzzwHfAxXCDo1gSdRTZE8WwQP9sHbAnPjS3AJD".decodeBase58().toList())

        val derivedAccounts = TimelockDerivedAccounts.newInstance(authority)

        val builtTransaction = TransactionBuilder.closeDormantAccount(
            authority = authority,
            timelockDerivedAccounts = derivedAccounts,
            destination = destination,
            nonce = nonce,
            recentBlockhash = blockhash,
            kreIndex = kreIndex
        )

        // Remove the signatures before comparison
        val transactionNoSignatures =
            com.getcode.solana.SolanaTransaction(
                transaction.message,
                listOf(Signature.zero, Signature.zero)
            )

        Assert.assertEquals(transactionNoSignatures.encode(), builtTransaction.encode())
    }

    @Test
    fun testTransferTransaction() {
        val (transaction, _) = SolanaTransaction.mockPrivateTransfer()

        val authority =
            PublicKey("Ddk7k7zMMWsp8fZB12wqbiADdXKQFWfwUUsxSo73JaQ9".decodeBase58().toList())
        val destination =
            PublicKey("2sDAFcEZkLd3mbm6SaZhifctkyB4NWsp94GMnfDs1BfR".decodeBase58().toList())
        val nonce =
            PublicKey("H7y8REaqickypzCfke3onJVKbbp8ELmaccFYeLZzJ2Wn".decodeBase58().toList())
        val blockhash =
            PublicKey("HjD8boPVb9pBVMQBdSzUMTt1HKTonwPsC3RibtXw44pK".decodeBase58().toList())

        val derivedAccounts = TimelockDerivedAccounts.newInstance(owner = authority)

        val builtTransaction = TransactionBuilder.transfer(
            timelockDerivedAccounts = derivedAccounts,
            destination = destination,
            amount = Kin.Companion.fromKin(2),
            nonce = nonce,
            recentBlockhash = blockhash,
            kreIndex = kreIndex
        )

        // Remove the signatures before comparison
        val transactionNoSignatures =
            com.getcode.solana.SolanaTransaction(
                transaction.message,
                listOf(Signature.zero, Signature.zero)
            )

        Assert.assertEquals(transactionNoSignatures.encode(), builtTransaction.encode())
    }
}