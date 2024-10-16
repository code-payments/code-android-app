package com.getcode.solana.builder

import com.getcode.mocks.SolanaTransaction
import com.getcode.model.Kin
import com.getcode.model.extensions.newInstance
import com.getcode.model.intents.actions.ActionType.Companion.kreIndex
import com.getcode.utils.decodeBase58
import junit.framework.Assert
import org.junit.Test

class TransactionBuilder_Test {

    @Test
    fun testCloseDormantAccountTransaction() {
        val (transaction, _) = SolanaTransaction.mockCloseDormantAccount()

        val authority =
            com.getcode.solana.keys.PublicKey(
                "Ed3GWPEdMiRXDMf7jU46fRwBF7n6ZZFGN3vH1dYAgME2".decodeBase58().toList()
            )
        val destination =
            com.getcode.solana.keys.PublicKey(
                "GEaVZeZ52Jn8xHPy4VKaXsHQ34E6pwfJGuYh8EsYQi6M".decodeBase58().toList()
            )
        val nonce =
            com.getcode.solana.keys.PublicKey(
                "27aoaJKNVtqKXRKQeMdKrtPMqAzcyYH5PGEgQ8x88TMH".decodeBase58().toList()
            )
        val blockhash =
            com.getcode.solana.keys.PublicKey(
                "7mezFVdzzwHfAxXCDo1gSdRTZE8WwQP9sHbAnPjS3AJD".decodeBase58().toList()
            )

        val derivedAccounts = com.getcode.solana.keys.TimelockDerivedAccounts.newInstance(authority)

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
                listOf(com.getcode.solana.keys.Signature.zero, com.getcode.solana.keys.Signature.zero)
            )

        Assert.assertEquals(transactionNoSignatures.encode(), builtTransaction.encode())
    }

    @Test
    fun testTransferTransaction() {
        val (transaction, _) = SolanaTransaction.mockPrivateTransfer()

        val authority =
            com.getcode.solana.keys.PublicKey(
                "Ddk7k7zMMWsp8fZB12wqbiADdXKQFWfwUUsxSo73JaQ9".decodeBase58().toList()
            )
        val destination =
            com.getcode.solana.keys.PublicKey(
                "2sDAFcEZkLd3mbm6SaZhifctkyB4NWsp94GMnfDs1BfR".decodeBase58().toList()
            )
        val nonce =
            com.getcode.solana.keys.PublicKey(
                "H7y8REaqickypzCfke3onJVKbbp8ELmaccFYeLZzJ2Wn".decodeBase58().toList()
            )
        val blockhash =
            com.getcode.solana.keys.PublicKey(
                "HjD8boPVb9pBVMQBdSzUMTt1HKTonwPsC3RibtXw44pK".decodeBase58().toList()
            )

        val derivedAccounts = com.getcode.solana.keys.TimelockDerivedAccounts.newInstance(owner = authority)

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
                listOf(com.getcode.solana.keys.Signature.zero, com.getcode.solana.keys.Signature.zero)
            )

        Assert.assertEquals(transactionNoSignatures.encode(), builtTransaction.encode())
    }
}