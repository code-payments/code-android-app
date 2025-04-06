package com.getcode.opencode.internal.solana.builder

import com.getcode.opencode.model.core.Fiat
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.TransferType
import com.getcode.solana.instructions.programs.MemoProgram_Memo
import com.getcode.solana.instructions.programs.SystemProgram_AdvanceNonce
import com.getcode.solana.instructions.programs.TimelockProgram_TransferWithAuthority
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Key32.Companion.subsidizer
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts
import com.getcode.vendor.Base58

object TransactionBuilder {

    fun transfer(
        timelockDerivedAccounts: TimelockDerivedAccounts,
        destination: PublicKey,
        amount: Fiat,
        nonce: PublicKey,
        recentBlockhash: Hash,
        kreIndex: Int
    ): SolanaTransaction {
        return SolanaTransaction.newInstance(
            payer = subsidizer,
            recentBlockhash = recentBlockhash,
            instructions = listOf(
                SystemProgram_AdvanceNonce(
                    nonce = nonce,
                    authority = subsidizer
                ).instruction(),

                MemoProgram_Memo.newInstance(
                    transferType = TransferType.p2p,
                    kreIndex = kreIndex
                ).instruction(),

                TimelockProgram_TransferWithAuthority(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    vaultOwner = timelockDerivedAccounts.owner,
                    timeAuthority = vmTimeAuthority,
                    destination = destination,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    quarks = amount.quarks.toLong()
                ).instruction(),
            )
        )
    }
}

val vmTimeAuthority = PublicKey(Base58.decode("f1ipC31qd2u88MjNYp1T4Cc7rnWfM9ivYpTV1Z8FHnD").toList())