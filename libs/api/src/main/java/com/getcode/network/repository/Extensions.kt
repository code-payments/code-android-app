package com.getcode.network.repository

import android.util.Base64
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.fromUbytes
import com.google.protobuf.ByteString
import com.getcode.vendor.Base58
import com.google.protobuf.MessageLite
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

fun isMock() = false

fun List<Byte>.toByteString(): ByteString = ByteString.copyFrom(this.toByteArray())
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

val List<Byte>.base58: String
    get() = Base58.encode(toByteArray())

val ByteArray.base58: String
    get() = Base58.encode(this)

fun PublicKey.toIntentId(): Model.IntentId {
    return Model.IntentId.newBuilder().setValue(this.byteArray.toByteString()).build()
}

fun ByteArray.toPublicKey(): PublicKey {
    return PublicKey(this.toList())
}

fun UByteArray.toPublicKey(): PublicKey {
    return PublicKey.fromUbytes(this.toList())
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

fun ByteArray.sha512(): ByteArray {
    return try {
        MessageDigest.getInstance("SHA-512")
            .apply { update(this@sha512) }
            .digest()

    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("SHA-512 not implemented")
    }
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

fun String.replaceParam(vararg value: String?): String {
    var result = this
    value.forEachIndexed { index, s ->
        result = result.replaceParam(index, s)
    }
    return result
}

fun String.replaceParam(index: Int = 0, value: String?): String {
    val param = "%${index + 1}\$s"
    return this.replace(param, value.orEmpty())
}

fun Ed25519.KeyPair.getPublicKeyBase58(): String {
    return org.kin.sdk.base.tools.Base58.encode(publicKeyBytes)
}

val Ed25519.KeyPair.publicKeyFromBytes: PublicKey
    get() = publicKeyBytes.toPublicKey()

fun List<Byte>.hexEncodedString(options: Set<HexEncodingOptions> = emptySet()): String {
    val hexDigits = if (options.contains(HexEncodingOptions.Uppercase))
        "0123456789ABCDEF"
    else
        "0123456789abcdef"

    val chars = CharArray(2 * size)
    var index = 0

    for (byte in toByteArray()) {
        chars[index++] = hexDigits[(byte.toInt() ushr 4) and 0xF]
        chars[index++] = hexDigits[byte.toInt() and 0xF]
    }

    return String(chars)
}

sealed interface HexEncodingOptions {
    data object Uppercase: HexEncodingOptions
}