package com.getcode.opencode.solana


open class MessageHeader(
    val requiredSignatures: Int,
    val readOnlySigners: Int,
    val readOnly: Int
) {
    fun encode(): ByteArray {
        return byteArrayOf(
            requiredSignatures.toByte(),
            readOnlySigners.toByte(),
            readOnly.toByte()
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageHeader

        if (requiredSignatures != other.requiredSignatures) return false
        if (readOnlySigners != other.readOnlySigners) return false
        if (readOnly != other.readOnly) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requiredSignatures
        result = 31 * result + readOnlySigners
        result = 31 * result + readOnly
        return result
    }

    companion object {
        const val length: Int = 3

        fun fromList(list: List<Byte>): MessageHeader {
            val data = list.map { it.toInt() }
            return MessageHeader(
                requiredSignatures = data[0],
                readOnlySigners = data[1],
                readOnly = data[2]
            )
        }
    }
}

internal val MessageHeader.description: String
    get() = "H{$requiredSignatures, $readOnlySigners, $readOnly}"