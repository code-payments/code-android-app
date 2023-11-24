package com.getcode.network.repository

import android.util.Base64
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.google.protobuf.ByteString
import com.getcode.utils.PhoneUtils
import com.getcode.vendor.Base58
import com.google.protobuf.MessageLite
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

fun isMock() = false

fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)

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

fun PublicKey.toIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

fun ByteArray.toHash(): Hash {
    return Hash(this.toList())
}

fun MessageLite.Builder.sign(owner: Ed25519.KeyPair): Model.Signature {
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)
    return Ed25519.sign(bos.toByteArray(), owner).toSignature()
}

fun String.decodeBase58(): ByteArray {
    return Base58.decode(this)
}

fun String.decodeBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

fun ByteArray.decodeBase64(): ByteArray {
    return Base64.decode(this, Base64.NO_WRAP)
}

fun ByteArray.encodeBase64(): String {
    return Base64.encodeToString(this, Base64.NO_WRAP)
}

fun ByteArray.encodeBase64ToArray(): ByteArray {
    return Base64.encode(this, Base64.NO_WRAP)
}

fun List<Int>.toByteList(): List<Byte> {
    return this.map { it.toByte() }
}

fun Long.toByteArray(): ByteArray =
    byteArrayOf(
        this.toByte(),
        (this ushr 8).toByte(),
        (this ushr 16).toByte(),
        (this ushr 24).toByte(),
        (this ushr 32).toByte(),
        (this ushr 40).toByte(),
        (this ushr 48).toByte(),
        (this ushr 56).toByte()
    )

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, "UTF-8")
}

fun String.replaceParam(value: String?): String {
    return this.replaceFirst("%s", value.orEmpty())
}

fun Ed25519.KeyPair.getPublicKeyBase58(): String {
    return org.kin.sdk.base.tools.Base58.encode(publicKeyBytes)
}

fun <T> Boolean.ifElse(ifTrue: T, ifFalse: T) = if (this) ifTrue else ifFalse

fun <T> Boolean?.ifElseNull(ifTrue: T, ifFalse: T, ifNull: T) = when {
    this == true -> ifTrue
    this == false -> ifFalse
    else -> ifNull
}