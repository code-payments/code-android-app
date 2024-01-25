package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Hash
import com.getcode.model.Kin
import com.getcode.model.intents.ServerParameter
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.SplitterCommitmentAccounts
import com.getcode.solana.keys.verifyContained
import com.getcode.solana.organizer.AccountCluster
import timber.log.Timber

class ActionPrivacyUpgrade(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair?,

    var source: AccountCluster,
    var originalActionID: Int,
    var originalCommitmentStateAccount: PublicKey,
    var originalAmount: Kin,
    var originalNonce: PublicKey,
    var originalRecentBlockhash: Hash,
    var treasury: PublicKey
) : ActionType() {
    val configCountRequirement: Int = 1

    override fun transactions(): List<SolanaTransaction> {
        serverParameter ?: throw ActionPrivacyUpgradeException.MissingServerParameterException()

        val privacyUpgrade = serverParameter?.parameter
        if (privacyUpgrade !is ServerParameter.Parameter.PermanentPrivacyUpgrade) {
            throw ActionPrivacyUpgradeException.MissingPrivacyUpgradeParameterException()
        }

        /// Validate the merkle proof and ensure that the original commitment
        /// accounts exist in the merkle tree provided by the server via the
        /// `merkleRoot` and `merkleProof` params

        val leaf = originalCommitmentStateAccount

        val isProofValid = leaf.verifyContained(
            privacyUpgrade.merkleRoot,
            privacyUpgrade.merkleProof
        )

        Timber.i("isProofValid: $isProofValid")

        if (!isProofValid) {
            throw ActionPrivacyUpgradeException.InvalidMerkleProofException()
        }

        // Server may provide the nonce and recentBlockhash and
        // it may match the original but we shouldn't trust it.
        // We'll user the original nonce and recentBlockhash that
        // the original transaction used.

        val splitterAccounts = SplitterCommitmentAccounts.newInstance(
            treasury = treasury,
            destination = privacyUpgrade.newCommitmentDestination,
            recentRoot = privacyUpgrade.merkleRoot,
            transcript = privacyUpgrade.newCommitmentTranscript,
            amount = privacyUpgrade.newCommitmentAmount
        )

        val transaction = TransactionBuilder.transfer(
            timelockDerivedAccounts = source.timelockAccounts,
            destination = splitterAccounts.vault.publicKey,
            amount = originalAmount,
            nonce = originalNonce,
            recentBlockhash = originalRecentBlockhash,
            kreIndex = kreIndex
        )

        return listOf(transaction)

    }

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply {
                this.id =
                    this@ActionPrivacyUpgrade.id
                this.permanentPrivacyUpgrade =
                    TransactionService.PermanentPrivacyUpgradeAction.newBuilder().apply {
                        this.actionId =
                            this@ActionPrivacyUpgrade.originalActionID
                    }.build()
            }
            .build()
    }

    companion object {
        fun newInstance(
            source: AccountCluster,
            originalActionID: Int,
            originalCommitmentStateAccount: PublicKey,
            originalAmount: Kin,
            originalNonce: PublicKey,
            originalRecentBlockhash: Hash,
            treasury: PublicKey
        ): ActionPrivacyUpgrade {
            return ActionPrivacyUpgrade(
                id = 0,
                signer = source.authority.keyPair,
                source = source,

                originalActionID = originalActionID,
                originalCommitmentStateAccount = originalCommitmentStateAccount,
                originalAmount = originalAmount,
                originalNonce = originalNonce,
                originalRecentBlockhash = originalRecentBlockhash,
                treasury = treasury
            )
        }
    }
}

sealed class ActionPrivacyUpgradeException : Exception() {
    class MissingServerParameterException : ActionPrivacyUpgradeException()
    class MissingPrivacyUpgradeParameterException : ActionPrivacyUpgradeException()
    class InvalidMerkleProofException : ActionPrivacyUpgradeException()
}
