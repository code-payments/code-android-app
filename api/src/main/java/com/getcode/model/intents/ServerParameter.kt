package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.keys.Hash
import com.getcode.model.Kin
import com.getcode.network.repository.toHash
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.PublicKey

class ServerParameter(
    val actionId: Int,
    val parameter: Parameter?,
    val configs: List<Config>
) {
    data class Config(val nonce: PublicKey, val blockhash: Hash)

    sealed class Parameter {
        data class TempPrivacy(val treasury: PublicKey, val recentRoot: Hash): Parameter()
        data class PermanentPrivacyUpgrade(
            val newCommitment: PublicKey,
            val newCommitmentTranscript: Hash,
            val newCommitmentDestination: PublicKey,
            val newCommitmentAmount: Kin,
            val merkleRoot: Hash,
            val merkleProof: List<Hash>,
        ): Parameter()

        companion object {
            fun newInstance(proto: TransactionService.ServerParameter): Parameter? {
                return when (proto.typeCase) {
                    TransactionService.ServerParameter.TypeCase.TEMPORARY_PRIVACY_TRANSFER -> {
                        val param = proto.temporaryPrivacyTransfer
                        val treasury = PublicKey(param.treasury.value.toByteArray().toList())
                        val recentRoot = Hash(param.recentRoot.value.toByteArray().toList())
                        return TempPrivacy(treasury, recentRoot)
                    }
                    TransactionService.ServerParameter.TypeCase.TEMPORARY_PRIVACY_EXCHANGE -> {
                        val param = proto.temporaryPrivacyExchange
                        val treasury = PublicKey(param.treasury.value.toByteArray().toList())
                        val recentRoot = Hash(param.recentRoot.value.toByteArray().toList())
                        return TempPrivacy(treasury, recentRoot)
                    }
                    TransactionService.ServerParameter.TypeCase.PERMANENT_PRIVACY_UPGRADE -> {
                        val param = proto.permanentPrivacyUpgrade
                        val newCommitment = PublicKey(param.newCommitment.value.toByteArray().toList())
                        val newCommitmentTranscript = Hash(param.newCommitmentTranscript.value.toByteArray().toList())
                        val newCommitmentDestination = PublicKey(param.newCommitmentDestination.value.toByteArray().toList())
                        val merkleRoot = Hash(param.merkleRoot.value.toByteArray().toList())

                        val merkleProof = param.merkleProofList.map {
                            Hash(it.value.toByteArray().toList())
                        }

                        PermanentPrivacyUpgrade(
                            newCommitment = newCommitment,
                            newCommitmentTranscript = newCommitmentTranscript,
                            newCommitmentDestination = newCommitmentDestination,
                            newCommitmentAmount = Kin.fromQuarks(quarks = param.newCommitmentAmount),
                            merkleRoot = merkleRoot,
                            merkleProof = merkleProof
                        )
                    }
                    TransactionService.ServerParameter.TypeCase.OPEN_ACCOUNT -> null
                    TransactionService.ServerParameter.TypeCase.CLOSE_EMPTY_ACCOUNT -> null
                    TransactionService.ServerParameter.TypeCase.CLOSE_DORMANT_ACCOUNT -> null
                    TransactionService.ServerParameter.TypeCase.NO_PRIVACY_WITHDRAW -> null
                    TransactionService.ServerParameter.TypeCase.TYPE_NOT_SET -> null
                    else -> null
                }
            }

        }
    }


    companion object {
        fun newInstance(proto: TransactionService.ServerParameter): ServerParameter {
            return ServerParameter(
                actionId = proto.actionId,
                parameter = Parameter.newInstance(proto),
                configs = proto.noncesList.map {
                    Config(
                        nonce = it.nonce.value.toByteArray().toPublicKey(),
                        blockhash = it.blockhash.value.toByteArray().toHash()
                    )
                }
            )
        }
    }
}


