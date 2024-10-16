package com.getcode.network.repository

import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.utils.toByteString
import com.google.protobuf.MessageLite
import java.io.ByteArrayOutputStream

fun isMock() = false

fun ByteArray.toUserId(): Model.UserId {
    return Model.UserId.newBuilder().setValue(this.toByteString()).build()
}

fun String.toPhoneNumber(): Model.PhoneNumber {
    return Model.PhoneNumber.newBuilder().setValue(this).build()
}

fun List<Byte>.toSolanaAccount(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.toByteArray().toByteString())
        .build()
}

fun ByteArray.toSolanaAccount(): Model.SolanaAccountId {
    return Model.SolanaAccountId.newBuilder().setValue(this.toByteString())
        .build()
}

fun ByteArray.toSignature(): Model.Signature {
    return Model.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

fun com.getcode.solana.keys.PublicKey.toIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

fun MessageLite.Builder.sign(owner: Ed25519.KeyPair): Model.Signature {
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)
    return Ed25519.sign(bos.toByteArray(), owner).toSignature()
}