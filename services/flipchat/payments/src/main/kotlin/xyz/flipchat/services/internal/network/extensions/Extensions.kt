package xyz.flipchat.services.internal.network.extensions

import com.codeinc.gen.common.v1.Model
import com.getcode.utils.toByteString

fun ByteArray.toSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

fun ByteArray.toSolanaAccount(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.toByteString())
        .build()
}