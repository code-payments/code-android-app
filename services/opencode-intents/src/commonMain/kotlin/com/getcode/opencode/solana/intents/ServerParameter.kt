package com.getcode.opencode.solana.intents

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


        companion object
    }

    companion object
}
