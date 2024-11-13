package xyz.flipchat.services.internal.network.utils

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.getcode.ed25519.Ed25519
import com.google.protobuf.GeneratedMessageLite
import xyz.flipchat.services.internal.network.extensions.asPublicKey
import xyz.flipchat.services.internal.network.extensions.toSignature
import java.io.ByteArrayOutputStream

internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.authenticate(owner: Ed25519.KeyPair): Flipchat.Auth {
    // dump message up until this point into a ByteArray
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)

    /**
     * sign message up to this point with owner and convert to [com.codeinc.flipchat.gen.common.v1.Signature]
     */
    val signature = Ed25519.sign(bos.toByteArray(), owner).toSignature()
    // build Auth.Keypair sub model
    val keyPairModel = Flipchat.Auth.KeyPair.newBuilder()
        .setPubKey(owner.asPublicKey())
        .apply { setSignature(signature) }
        .build()

    // return Auth model
    return Flipchat.Auth.newBuilder()
        .setKeyPair(keyPairModel)
        .build()
}
