package com.getcode.network.repository

import com.codeinc.gen.common.v1.CodeModel
import com.getcode.ed25519.Ed25519
import com.getcode.utils.toByteString
import com.google.protobuf.MessageLite
import java.io.ByteArrayOutputStream

fun isMock() = false

fun ByteArray.toUserId(): CodeModel.UserId {
    return CodeModel.UserId.newBuilder().setValue(this.toByteString()).build()
}

fun String.toPhoneNumber(): CodeModel.PhoneNumber {
    return CodeModel.PhoneNumber.newBuilder().setValue(this).build()
}

fun List<Byte>.toSolanaAccount(): CodeModel.SolanaAccountId {
    return CodeModel.SolanaAccountId.newBuilder().setValue(this.toByteArray().toByteString())
        .build()
}

fun ByteArray.toSolanaAccount(): CodeModel.SolanaAccountId {
    return CodeModel.SolanaAccountId.newBuilder().setValue(this.toByteString())
        .build()
}

fun ByteArray.toSignature(): CodeModel.Signature {
    return CodeModel.Signature.newBuilder().setValue(this.toByteString())
        .build()
}

fun com.getcode.solana.keys.PublicKey.toIntentId(): CodeModel.IntentId {
    return CodeModel.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

fun MessageLite.Builder.sign(owner: Ed25519.KeyPair): CodeModel.Signature {
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)
    return Ed25519.sign(bos.toByteArray(), owner).toSignature()
}