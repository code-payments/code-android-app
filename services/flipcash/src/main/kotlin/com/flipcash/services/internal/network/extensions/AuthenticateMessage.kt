package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.common.v1.Common
import com.getcode.ed25519.Ed25519
import com.google.protobuf.GeneratedMessageLite
import java.io.ByteArrayOutputStream

internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.authenticate(owner: Ed25519.KeyPair): Common.Auth {
    // dump message up until this point into a ByteArray
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)

    /**
     * sign message up to this point with owner and convert to [com.codeinc.flipchat.gen.common.v1.Signature]
     */
    val signature = Ed25519.sign(bos.toByteArray(), owner).asSignature()
    // build Auth.Keypair sub model
    val keyPairModel = Common.Auth.KeyPair.newBuilder()
        .setPubKey(owner.asPublicKey())
        .apply { setSignature(signature) }
        .build()

    // return Auth model
    return Common.Auth.newBuilder()
        .setKeyPair(keyPairModel)
        .build()
}
