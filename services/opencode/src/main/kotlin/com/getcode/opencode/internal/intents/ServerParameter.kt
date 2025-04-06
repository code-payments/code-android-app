package com.getcode.opencode.internal.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.toHash
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey

class ServerParameter(
    val actionId: Int,
    val parameter: Parameter?,
    val configs: List<Config>
) {
    data class Config(val nonce: PublicKey, val blockhash: Hash)

    sealed class Parameter {
        data class FeePayment(val publicKey: PublicKey): Parameter()

        companion object {
            fun newInstance(proto: TransactionService.ServerParameter): Parameter? {
                return when (proto.typeCase) {
                    TransactionService.ServerParameter.TypeCase.FEE_PAYMENT -> {
                        val param = proto.feePayment

                        // PublicKey will be `nil` for .thirdParty fee payments
                        val optionalDestination = PublicKey(
                            param.codeDestination.value.toByteArray().toList()
                        )
                        FeePayment(optionalDestination)
                    }
                    TransactionService.ServerParameter.TypeCase.OPEN_ACCOUNT,
                    TransactionService.ServerParameter.TypeCase.NO_PRIVACY_WITHDRAW,
                    TransactionService.ServerParameter.TypeCase.TYPE_NOT_SET,
                    TransactionService.ServerParameter.TypeCase.NO_PRIVACY_TRANSFER -> null
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


