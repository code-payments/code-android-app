package com.getcode.model.extensions

import com.getcode.model.Kin
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.SplitterCommitmentAccounts
import com.getcode.solana.keys.SplitterTranscript
import com.getcode.solana.organizer.AccountCluster

fun SplitterCommitmentAccounts.Companion.newInstance(
    source: AccountCluster,
    destination: PublicKey,
    amount: Kin,
    treasury: PublicKey,
    recentRoot: Hash,
    intentId: PublicKey,
    actionId: Int
): SplitterCommitmentAccounts {
    val transcript = SplitterTranscript(
        intentId = intentId,
        actionId = actionId,
        amount = amount,
        source = source.vaultPublicKey,
        destination = destination
    )

    return newInstance(
        treasury = treasury,
        destination = destination,
        recentRoot = recentRoot,
        transcript = transcript.transcriptHash,
        amount = amount
    )
}

fun SplitterCommitmentAccounts.Companion.newInstance(
    treasury: PublicKey,
    destination: PublicKey,
    recentRoot: Hash,
    transcript: Hash,
    amount: Kin
): SplitterCommitmentAccounts {
    val state = PublicKey.deriveCommitmentStateAccount(
        treasury = treasury,
        recentRoot = recentRoot,
        transcript = transcript,
        destination = destination,
        amount = amount
    )

    val vault = PublicKey.deriveCommitmentVaultAccount(
        treasury = treasury,
        commitmentState = state.publicKey
    )

    return SplitterCommitmentAccounts(
        treasury = treasury,
        destination = destination,
        recentRoot = recentRoot,
        transcript = transcript,
        state = state,
        vault = vault,
    )
}