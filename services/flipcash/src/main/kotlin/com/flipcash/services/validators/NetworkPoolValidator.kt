package com.flipcash.services.validators

import com.flipcash.services.internal.network.extensions.toProto
import com.flipcash.services.models.NetworkPool
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.PublicKey
import javax.inject.Inject

class NetworkPoolValidator @Inject constructor(): Validator<NetworkPool> {
    override fun isValid(param: NetworkPool): Boolean {
        return Ed25519.verify(
            param.rendezvousSignature.toByteArray(),
            param.metadata.toProto().toByteArray(),
            PublicKey(param.metadata.id).byteArray
        )
    }
}