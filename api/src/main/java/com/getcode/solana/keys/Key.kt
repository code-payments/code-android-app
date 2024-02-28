package com.getcode.solana.keys

import com.getcode.network.repository.encodeBase64
import org.kin.sdk.base.tools.Base58

// Make KeyType an interface to define contract without exposing bytes directly
interface KeyType {
    val size: Int
    fun toByteArray(): ByteArray
    fun base58(): String
    fun base64(): String
}

// Implement KeyType with a secure and encapsulated class
abstract class AbstractKeyType(private val bytes: ByteArray) : KeyType {

    init {
        // Perform validation on initialization to ensure bytes meet the specific size requirement
        require(bytes.size == size) { "Invalid key size: expected $size, got ${bytes.size}" }
    }

    override fun toByteArray(): ByteArray = bytes.copyOf()

    override fun base58(): String = Base58.encode(toByteArray())

    override fun base64(): String = toByteArray().encodeBase64()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractKeyType) return false

        return toByteArray().contentEquals(other.toByteArray())
    }

    override fun hashCode(): Int = toByteArray().contentHashCode()
}

class Key16(bytes: ByteArray) : AbstractKeyType(bytes) {
    override val size: Int get() = LENGTH_16
}

open class Key32(bytes: ByteArray) : AbstractKeyType(bytes) {
    // Secondary constructor for Base58 string initialization
    constructor(base58: String) : this(Base58.decode(base58))

    override val size: Int get() = LENGTH_32

    companion object {
        // Use secure initialization for predefined keys
        val kinMint = Key32(Base58.decode("kinXdEcpDQeHPEuQnqmUgtYykqKGVFq6CeVX5iAHJq6"))
        val subsidizer = Key32(Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR"))
        val timeAuthority = Key32(Base58.decode("codeHy87wGD5oMRLG75qKqsSi1vWE3oxNyYmXo5F9YR"))
        val splitter = Key32(Base58.decode("spLit2eb13Tz93if6aJM136nUWki5PVUsoEjcUjwpwW"))
        val mock = Key32(Base58.decode("EBDRoayCDDUvDgCimta45ajQeXbexv7aKqJubruqpyvu"))
        val zero = Key32(ByteArray(LENGTH_32))
    }
}

open class Key64(bytes: ByteArray) : AbstractKeyType(bytes) {
    override val size: Int get() = LENGTH_64
}

const val LENGTH_16 = 16
const val LENGTH_32 = 32
const val LENGTH_64 = 64
