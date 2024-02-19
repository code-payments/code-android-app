package com.getcode.solana.builder

import com.getcode.solana.keys.Hash
import com.getcode.model.Kin
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.TransferType
import com.getcode.solana.instructions.programs.*
import com.getcode.solana.keys.Key32.Companion.subsidizer
import com.getcode.solana.keys.Key32.Companion.timeAuthority
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts

object TransactionBuilder {

    fun closeEmptyAccount(
        timelockDerivedAccounts: TimelockDerivedAccounts,
        maxDustAmount: Kin,
        nonce: PublicKey,
        recentBlockhash: Hash,
        legacy: Boolean = false
    ): SolanaTransaction {
        return SolanaTransaction.newInstance(
            payer = subsidizer,
            recentBlockhash = recentBlockhash,
            instructions = listOf(
                SystemProgram_AdvanceNonce(
                    nonce = nonce,
                    authority = subsidizer
                ).instruction(),
                TimelockProgram_BurnDustWithAuthority(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    vaultOwner = timelockDerivedAccounts.owner,
                    timeAuthority = timeAuthority,
                    mint = Mint.kin,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    maxAmount = maxDustAmount,
                    legacy = legacy
                ).instruction(),

                TimelockProgram_CloseAccounts(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    closeAuthority = subsidizer,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    legacy = legacy
                ).instruction(),
            )
        )
    }


    fun transfer(
        timelockDerivedAccounts: TimelockDerivedAccounts,
        destination: PublicKey,
        amount: Kin,
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
                    timeAuthority = timeAuthority,
                    destination = destination,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    kin = amount
                ).instruction(),
            )
        )
    }

    fun closeDormantAccount(
        authority: PublicKey,
        timelockDerivedAccounts: TimelockDerivedAccounts,
        destination: PublicKey,
        nonce: PublicKey,
        recentBlockhash: Hash,
        kreIndex: Int,
        legacy: Boolean = false
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

                TimelockProgram_RevokeLockWithAuthority(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    closeAuthority = subsidizer,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    legacy = legacy
                ).instruction(),

                TimelockProgram_DeactivateLock(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vaultOwner = authority,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    legacy = legacy
                ).instruction(),

                TimelockProgram_Withdraw(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    vaultOwner = authority,
                    destination = destination,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    legacy = legacy
                ).instruction(),

                TimelockProgram_CloseAccounts(
                    timelock = timelockDerivedAccounts.state.publicKey,
                    vault = timelockDerivedAccounts.vault.publicKey,
                    closeAuthority = subsidizer,
                    payer = subsidizer,
                    bump = timelockDerivedAccounts.state.bump.toByte(),
                    legacy = legacy
                ).instruction(),
                )
        )
    }

}