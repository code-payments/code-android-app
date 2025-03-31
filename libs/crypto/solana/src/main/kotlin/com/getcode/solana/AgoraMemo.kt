package com.getcode.solana

import com.getcode.solana.AgoraMemo.Companion.maxMagicByteIndicatorSize
import com.getcode.utils.decodeBase64
import com.getcode.utils.encodeBase64ToArray
import org.kin.sdk.base.tools.byteArrayToInt
import org.kin.sdk.base.tools.shl
import org.kin.sdk.base.tools.subByteArray
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.experimental.xor

class AgoraMemo(
    val magicByte: MagicByte = MagicByte.default,
    val version: Byte = 1,
    val transferType: TransferType,
    val appIndex: Int,
    bytes: List<Byte> = listOf(),
) {
    val bytes: List<Byte> = List(byteLength) { bytes.getOrNull(it) ?: 0 }

    /**
     * A memo format understood by the Agora services.
     * magicByteIndicator    2 bits   | less than 4
     * version               3 bits   | less than 8
     * typeId                5 bits   | less than 32
     * appIdx                16 bits  | less than 65,536
     * foreignKey            230 bits | Base64 Encoded String of [230 bits + (2 zeros padding)]
     */

    /**
     * Fields below are packed from LSB to MSB order:
     * magicByteIndicator             2 bits | less than 4
     * version                        3 bits | less than 8
     * typeId                         5 bits | less than 32
     * appIdx                        16 bits | less than 65,536
     * foreignKey                   230 bits | Often a SHA-224 of an InvoiceList but could be anything
     */

    fun encode(): ByteArray {
        val result = ByteArray(totalByteCount)

        result[0] = magicByte.rawValue
        result[0] = result[0] xor (version shl 2)
        result[0] = result[0] xor ((transferType.rawValue and 0x7).toByte() shl 5)

        result[1] = ((transferType.rawValue and 0x1c) shr 2).toByte()
        result[1] = result[1] xor ((appIndex and 0x3f).toByte() shl 2)

        result[2] = ((appIndex and 0x3fc0) shr 6).toByte()

        result[3] = ((appIndex and 0xc000) shr 14).toByte()

        // Encode foreign key
        result[3] = result[3] xor ((bytes[0] and 0x3f) shl 2)

        // Insert the rest of the fk. since each loop references fk[n] and fk[n + 1], the upper bound is offset by 3 instead of 4.
        for (i in 4 until 3 + bytes.size) {
            // apply last 2-bits of current byte
            // apply first 6-bits of next byte
            result[i] = (bytes[i - 4].toInt() shr 6).toByte() and 0x3
            result[i] = result[i] xor ((bytes[i - 3].toInt() and 0x3f).toByte() shl 2)
        }

        // If the foreign key is less than 29 bytes, the last 2 bits of the FK can be included in the memo
        if (bytes.size < 29) {
            result[bytes.size + 3] = (bytes[bytes.size - 1].toInt() shr 6).toByte() and 0x3
        }

        return result.encodeBase64ToArray()
    }


    companion object {
        const val magicByteBitLength: Int = 2
        const val versionBitLength: Int = 3
        const val transferTypeBitLength: Int = 5
        const val appIndexBitLength: Int = 16
        const val foreignKeyBitLength: Int = 230

        const val maxMagicByteIndicatorSize: Int = 1 shl magicByteBitLength

        const val byteLength: Int = foreignKeyBitLength / 8

        const val magicByteMask: Int = 0x3
        const val versionMask: Int = 0x1C
        const val transferTypeMask: Int = 0x3E0
        const val appIndexMask: Int = 0x3FFFC00

        const val magicByteIndicatorBitOffset: Int = 0

        const val totalLowerByteCount: Int =
            (magicByteBitLength + versionBitLength + transferTypeBitLength + appIndexBitLength) / 8
        const val totalByteCount: Int =
            (magicByteBitLength + versionBitLength + transferTypeBitLength + appIndexBitLength + foreignKeyBitLength) / 8


        fun newInstance(data: List<Byte>): AgoraMemo {
            val content = data.toByteArray().decodeBase64()
            val header = content.subByteArray(0, 4).byteArrayToInt()

            val byte = (header and magicByteMask) shr 0
            val version = (header and versionMask) shr 2
            val transferTypeValue = (header and transferTypeMask) shr 5
            val appIndex = (header and appIndexMask) shr 10

            val magicByte = MagicByte(byte.toByte())
            val transferType = TransferType.getByValue(transferTypeValue.toByte())

            if (transferType == TransferType.unknown) {
                throw IllegalArgumentException("Invalid Value")
            }

            val bytes = ByteArray(byteLength)

            for (i in 0 until byteLength) {
                bytes[i] = bytes[i] or (content[i + 3].toInt() shr 2).toByte() and 0x3F
                bytes[i] = bytes[i] or ((content[i + 4] and 0x3) shl 6)
            }

            return AgoraMemo(
                magicByte = magicByte,
                version = version.toByte(),
                transferType = transferType,
                appIndex = appIndex,
                bytes = bytes.toList()
            )

        }

    }
}

class MagicByte(val rawValue: Byte) {
    init {
        val isValid = rawValue in 1 until maxMagicByteIndicatorSize
        //rawValue > 0 && rawValue < maxMagicByteIndicatorSize
        if (!isValid) throw IllegalArgumentException("Invalid Value")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MagicByte

        if (rawValue != other.rawValue) return false

        return true
    }

    override fun hashCode(): Int {
        return rawValue.toInt()
    }

    companion object {
        val default = MagicByte(rawValue = 1)
    }
}

enum class TransferType(val rawValue: Int) {
    unknown(Byte.MAX_VALUE.toInt() and 0xFF),

    /// When none of the other types are appropriate for the use case.
    none(0),

    /// Use when transferring Kin to a user for some performed action.
    earn(1),

    /// Use when transferring Kin due to purchasing something.
    spend(2),

    /// Use when transferring Kin where it does not constitute an `earn` or `spend`.
    p2p(3);

    companion object {
        fun getByValue(rawValue: Byte): TransferType {
            return when (rawValue.toInt()) {
                0 -> none
                1 -> earn
                2 -> spend
                3 -> p2p
                else -> unknown
            }
        }
    }
}

enum class Error {
    invalidData,
    invalidMagicByte,
    invalidVersion,
    invalidTransferType,
    invalidAppIndex,
}
