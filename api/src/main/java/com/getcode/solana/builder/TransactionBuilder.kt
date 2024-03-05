package com.getcode.solana.builder

import com.getcode.solana.keys.Hash
import com.getcode.model.Kin
import com.getcode.model.intents.SwapConfigParameters
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.TransferType
import com.getcode.solana.instructions.programs.*
import com.getcode.solana.keys.Key32.Companion.mock
import com.getcode.solana.keys.Key32.Companion.subsidizer
import com.getcode.solana.keys.Key32.Companion.timeAuthority
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PreSwapStateAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.AccountCluster
import org.kin.sdk.base.models.Key

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

    // Swap performs an on-chain swap. The high-level flow mirrors SubmitIntent
    // closely. However, due to the time-sensitive nature and unreliability of
    // swaps, they do not fit within the broader intent system. This results in
    // a few key differences:
    //  * Transactions are submitted on a best-effort basis outside of the Code
    //    Sequencer within the RPC handler
    //  * Balance changes are applied after the transaction has finalized
    //  * Transactions use recent blockhashes over a nonce
    //
    // The transaction will have the following instruction format:
    //   1. ComputeBudget::SetComputeUnitLimit
    //   2. ComputeBudget::SetComputeUnitPrice
    //   3. SwapValidator::PreSwap
    //   4. Dynamic swap instruction
    //   5. SwapValidator::PostSwap
    //
    // Note: Currently limited to swapping USDC to Kin.
    // Note: Kin is deposited into the primary account.
    //
    fun swap(fromUsdc: AccountCluster, toPrimary: PublicKey, parameters: SwapConfigParameters): SolanaTransaction {
        val payer = parameters.payer
        val destination = toPrimary

        val stateAccount = PreSwapStateAccount.newInstance(
            owner = mock,
            source = fromUsdc.vaultPublicKey,
            destination = destination,
            nonce = parameters.nonce
        )

        val remainingAccounts = parameters.swapAccounts.filter {
            (it.isSigner || it.isWritable) &&
                    (it.publicKey.base58() != fromUsdc.authorityPublicKey.base58() &&
                            it.publicKey.base58() != fromUsdc.vaultPublicKey.base58() &&
                            it.publicKey.base58() != destination.base58())
        }

        return SolanaTransaction.newInstance(
            payer = payer,
            recentBlockhash = parameters.blockHash,
            instructions = listOf(
                ComputeBudgetProgram_SetComputeUnitLimit(
                    limit = parameters.computeUnitLimit,
                    bump = stateAccount.state.bump.toByte(),
                ).instruction(),
                ComputeBudgetProgram_SetComputeUnitPrice(
                    microLamports = parameters.computeUnitPrice,
                    bump = stateAccount.state.bump.toByte(),
                ).instruction(),
                SwapValidatorProgram_PreSwap(
                    preSwapState = stateAccount.state.publicKey,
                    user = fromUsdc.authorityPublicKey,
                    source = fromUsdc.vaultPublicKey,
                    destination = destination,
                    nonce = parameters.nonce,
                    payer = payer,
                    remainingAccounts = remainingAccounts.map {
                        val publicKey = PublicKey.fromBase58(it.publicKey.base58())
                        AccountMeta(publicKey, it.isSigner, it.isWritable, it.isPayer, it.isProgram)
                    },
                ).instruction(),
                Instruction(
                    program = parameters.swapProgram,
                    accounts = parameters.swapAccounts.map {
                        val publicKey = PublicKey.fromBase58(it.publicKey.base58())
                        AccountMeta(publicKey, it.isSigner, it.isWritable, it.isPayer, it.isProgram)
                    },
                    data = parameters.swapData.toList(),
                ),
                SwapValidatorProgram_PostSwap(
                    stateBump = stateAccount.state.bump.toByte(),
                    maxToSend = parameters.maxToSend,
                    minToReceive = parameters.minToReceive,
                    preSwapState = stateAccount.state.publicKey,
                    source = fromUsdc.vaultPublicKey,
                    destination = destination,
                    payer = payer,
                ).instruction()
            )
        )
    }

}