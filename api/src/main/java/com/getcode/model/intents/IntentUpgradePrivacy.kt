package com.getcode.model.intents

import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.Signature
import com.getcode.model.Kin
import com.getcode.model.UpgradeableIntent
import com.getcode.model.intents.actions.ActionPrivacyUpgrade
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.SplitterCommitmentAccounts
import com.getcode.solana.organizer.AccountCluster

class IntentUpgradePrivacy(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setUpgradePrivacy(TransactionService.UpgradePrivacyMetadata.getDefaultInstance())
            .build()
    }

    companion object {
        fun newInstance(
            mnemonic: MnemonicPhrase,
            upgradeableIntent: UpgradeableIntent
        ): IntentUpgradePrivacy {
            val actionsMapped = upgradeableIntent.actions.map { upgradeableAction ->
                val actionAmount = upgradeableAction.originalAmount
                val originalDestination = upgradeableAction.originalDestination
                val treasury = upgradeableAction.treasury
                val recentRoot = upgradeableAction.recentRoot
                val originalNonce = upgradeableAction.originalNonce
                val originalRecentBlockhash = upgradeableAction.originalRecentBlockhash

                val sourceCluster = AccountCluster.using(
                    type = upgradeableAction.sourceAccountType,
                    index = upgradeableAction.sourceDerivationIndex.toInt(),
                    mnemonic = mnemonic
                )

                // Validate the server isn't malicious and is providing
                // the original details of the transaction
                validate(
                    transactionData = upgradeableAction.transactionBlob,
                    clientSignature = upgradeableAction.clientSignature,
                    intentId = upgradeableIntent.id,
                    actionId = upgradeableAction.id,
                    amount = actionAmount,
                    source = sourceCluster,
                    destination = originalDestination,
                    originalNonce = originalNonce,
                    treasury = treasury,
                    recentRoot = recentRoot
                )

                // We have to derive the original commitment accounts because
                // we'll need to verify whether the commitment state account
                // is part of the merkle tree provided by server paramaeters
                val originalSplitterAccounts = SplitterCommitmentAccounts.newInstance(
                    source = sourceCluster,
                    destination = originalDestination,
                    amount = actionAmount,
                    treasury = treasury,
                    recentRoot = recentRoot,
                    intentId = upgradeableIntent.id,
                    actionId = upgradeableAction.id
                )

                ActionPrivacyUpgrade.newInstance(
                    source = sourceCluster,
                    originalActionID = upgradeableAction.id,
                    originalCommitmentStateAccount = originalSplitterAccounts.state.publicKey,
                    originalAmount = actionAmount,
                    originalNonce = originalNonce,
                    originalRecentBlockhash = originalRecentBlockhash,
                    treasury = treasury
                )
            }

            return IntentUpgradePrivacy(
                id = upgradeableIntent.id,
                actionGroup = ActionGroup().apply { this.actions = actionsMapped }
            )
        }


        fun validate(
            transactionData: List<Byte>,
            clientSignature: Signature,
            intentId: PublicKey,
            actionId: Int,
            amount: Kin,
            source: AccountCluster,
            destination: PublicKey,
            originalNonce: PublicKey,
            treasury: PublicKey,
            recentRoot: Hash
        ) {
            val transaction = SolanaTransaction.fromList(transactionData)
                ?: throw IntentUpgradePrivacyException.FailedToParseTransactionException()

            val originalTransfer = ActionTransfer.newInstance(
                kind = ActionTransfer.Kind.TempPrivacyTransfer,
                intentId = intentId,
                amount = amount,
                source = source,
                destination = destination
            )

            originalTransfer.id = actionId
            originalTransfer.serverParameter = ServerParameter(
                actionId = actionId,
                parameter = ServerParameter.Parameter.TempPrivacy(
                    treasury = treasury,
                    recentRoot = recentRoot
                ),
                configs = listOf(
                    ServerParameter.Config(
                        nonce = originalNonce,
                        blockhash = transaction.recentBlockhash
                    )
                ),
            )

            val originalTransaction = originalTransfer.transactions()[0]

            if (originalTransaction.encode() != transactionData) {
                throw IntentUpgradePrivacyException.TransactionMismatchException()
            }

            // (Optional) Reach into transaction and make sure the source is the same
            val signature = originalTransaction.sign(source.authority.keyPair).firstOrNull()

            if (signature != clientSignature) {
                throw IntentUpgradePrivacyException.SignatureMismatchException()
            }
        }
    }
}

sealed class IntentUpgradePrivacyException : Exception() {
    class FailedToParseTransactionException : IntentUpgradePrivacyException()
    class TransactionMismatchException : IntentUpgradePrivacyException()
    class SignatureMismatchException : IntentUpgradePrivacyException()
}