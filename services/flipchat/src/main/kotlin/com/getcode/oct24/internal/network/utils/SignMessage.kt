package com.getcode.oct24.internal.network.utils

import com.codeinc.flipchat.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.oct24.internal.network.extensions.toSignature
import com.google.protobuf.GeneratedMessageLite
import java.io.ByteArrayOutputStream

internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.sign(owner: Ed25519.KeyPair): Model.Signature {
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)
    return Ed25519.sign(bos.toByteArray(), owner).toSignature()
}
