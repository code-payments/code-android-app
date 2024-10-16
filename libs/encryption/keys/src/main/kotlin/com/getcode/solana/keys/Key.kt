package com.getcode.solana.keys

import com.getcode.utils.encodeBase64
import org.kin.sdk.base.tools.Base58

abstract class KeyType(bytes: List<Byte>) {
    abstract val size: Int
    var bytes: List<Byte> = bytes
        set(v) {
            if (v.size == size) field = v
        }

    val byteArray = bytes.toByteArray()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KeyType

        if (bytes != other.bytes) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.hashCode()
    }

}

fun KeyType.base58(): String = Base58.encode(bytes.toByteArray())
fun KeyType.base64(): String = bytes.toByteArray().encodeBase64()

class Key16(bytes: List<Byte>) : KeyType(bytes) {
    override val size: Int get() = LENGTH_16
}
open class Key32(bytes: List<Byte>) : KeyType(bytes) {

    constructor(base58: String) : this(Base58.decode(base58).toList())

    override val size: Int get() = LENGTH_32

    companion object {
        val kinMint = PublicKey(Base58.decode("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6").toList())

        val subsidizer = PublicKey(Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR").toList())
        val timeAuthority = PublicKey(Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR").toList())
        val splitter = PublicKey(Base58.decode("spLit2eb13Tz93if6aJM136nUWki5PVUsoEjcUjwpwW").toList())

        val mock = PublicKey(Base58.decode("EBDRoayCDDUvDgCimta45ajQeXbexv7aKqJubruqpyvu").toList())

        val zero = Key32(ByteArray(LENGTH_32).toList())

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as Key32
        if (size == other.size && bytes == other.bytes) return true

        return false
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + size
        return result
    }
}
open class Key64(bytes: List<Byte>) : KeyType(bytes) {

    constructor(base58: String): this (Base58.decode(base58).toList())

    override val size: Int get() = LENGTH_64
}

open class CurvePrivate

const val LENGTH_16 = 16
const val LENGTH_32 = 32
const val LENGTH_64 = 64
