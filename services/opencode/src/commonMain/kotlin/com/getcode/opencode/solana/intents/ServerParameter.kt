package com.getcode.opencode.solana.intents

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
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
            fun newInstance(proto: OcpTransactionService.ServerParameter): Parameter? {
                return when (proto.typeCase) {
                    OcpTransactionService.ServerParameter.TypeCase.FEE_PAYMENT -> {
                        val param = proto.feePayment

                        // PublicKey will be `nil` for .thirdParty fee payments
                        val optionalDestination = PublicKey(
                            param.destination.value.toByteArray().toList()
                        )
                        FeePayment(optionalDestination)
                    }
                    OcpTransactionService.ServerParameter.TypeCase.OPEN_ACCOUNT,
                    OcpTransactionService.ServerParameter.TypeCase.NO_PRIVACY_WITHDRAW,
                    OcpTransactionService.ServerParameter.TypeCase.TYPE_NOT_SET,
                    OcpTransactionService.ServerParameter.TypeCase.NO_PRIVACY_TRANSFER -> null
                    else -> null
                }
            }
        }
    }

    companion object {
        fun newInstance(proto: OcpTransactionService.ServerParameter): ServerParameter {
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


