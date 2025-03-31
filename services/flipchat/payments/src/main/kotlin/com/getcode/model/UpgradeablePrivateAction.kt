package com.getcode.model

import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.instructions.programs.SystemProgram_AdvanceNonce
import com.getcode.solana.instructions.programs.TimelockProgram_TransferWithAuthority
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import com.getcode.solana.organizer.AccountType

class UpgradeablePrivateAction(
    val id: Int,
    val transactionBlob: List<Byte>,
    val clientSignature: Signature,
    val sourceAccountType: AccountType,
    val sourceDerivationIndex: Long,
    val originalDestination: PublicKey,
    val originalAmount: Kin,
    val treasury: PublicKey,
    val recentRoot: Hash,

    val transaction: SolanaTransaction,
    val originalNonce: PublicKey,
    val originalCommitment: PublicKey,
    val originalRecentBlockhash: Hash
) {
    companion object {
        fun newInstance(
            id: Int,
            transactionBlob: List<Byte>,
            clientSignature: Signature,
            sourceAccountType: AccountType,
            sourceDerivationIndex: Long,
            originalDestination: PublicKey,
            originalAmount: Kin,
            treasury: PublicKey,
            recentRoot: Hash
        ): UpgradeablePrivateAction {
            val transaction: SolanaTransaction = SolanaTransaction.fromList(transactionBlob)
                ?: throw UpgradeablePrivateActionException.FailedToParseTransactionException()

            val nonceInstruction =
                transaction.findInstruction<SystemProgram_AdvanceNonce>(SystemProgram_AdvanceNonce::newInstance)
                    ?: throw UpgradeablePrivateActionException.MissingOriginalNonceException()

            val transferInstruction =
                transaction.findInstruction<TimelockProgram_TransferWithAuthority>(
                    TimelockProgram_TransferWithAuthority::newInstance
                )
                    ?: throw UpgradeablePrivateActionException.MissingOriginalCommitmentException()


            return UpgradeablePrivateAction(
                id = id,
                transactionBlob = transactionBlob,
                clientSignature = clientSignature,
                sourceAccountType = sourceAccountType,
                sourceDerivationIndex = sourceDerivationIndex,
                originalDestination = originalDestination,
                originalAmount = originalAmount,
                treasury = treasury,
                recentRoot = recentRoot,
                transaction = transaction,
                originalNonce = nonceInstruction.nonce,
                originalCommitment = transferInstruction.destination,
                originalRecentBlockhash = transaction.recentBlockhash
            )
        }

        fun newInstance(proto: TransactionService.UpgradeableIntent.UpgradeablePrivateAction): UpgradeablePrivateAction {
            val signature = Signature(
                proto.clientSignature.value.toByteArray().toList()
            )
            val accountType =
                AccountType.newInstance(proto.sourceAccountType)
                    ?: throw UpgradeablePrivateActionException.DeserializationFailedException()
            val originalDestination =
                PublicKey(
                    proto.originalDestination.value.toByteArray().toList()
                )
            val treasury =
                PublicKey(proto.treasury.value.toByteArray().toList())
            val recentRoot =
                Hash(proto.recentRoot.value.toByteArray().toList())

            return newInstance(
                id = proto.actionId,
                transactionBlob = proto.transactionBlob.value.toByteArray().toList(),
                clientSignature = signature,
                sourceAccountType = accountType,
                sourceDerivationIndex = proto.sourceDerivationIndex,
                originalDestination = originalDestination,
                originalAmount = Kin.fromQuarks(proto.originalAmount),
                treasury = treasury,
                recentRoot = recentRoot
            )
        }
    }

    sealed class UpgradeablePrivateActionException : Exception() {
        class MissingOriginalNonceException : UpgradeablePrivateActionException()
        class MissingOriginalCommitmentException : UpgradeablePrivateActionException()
        class FailedToParseTransactionException : UpgradeablePrivateActionException()
        class DeserializationFailedException : UpgradeablePrivateActionException()
    }
}
