package com.getcode.oct24.internal.network.utils

import com.codeinc.flipchat.gen.common.v1.Flipchat
import com.getcode.ed25519.Ed25519
import com.getcode.oct24.internal.network.extensions.toSignature
import com.google.protobuf.GeneratedMessageLite
import java.io.ByteArrayOutputStream

internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.sign(owner: Ed25519.KeyPair): Flipchat.Signature {
    // dump message up until this point into a ByteArray
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)

    /**
     * sign message up to this point with owner and return as [com.codeinc.flipchat.gen.common.v1.Signature]
     */
    return Ed25519.sign(bos.toByteArray(), owner).toSignature()
}