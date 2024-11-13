package com.getcode.model

import org.kin.sdk.base.models.QuarkAmount
import org.kin.sdk.base.models.solana.read
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray
import org.kin.sdk.base.tools.toByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID


data class KinCode(val type: Type = Type.None, val amount: QuarkAmount, val nonce: Nonce) {

    sealed class Type(val value: Int) {
        object Unknown : Type(-1)
        object None : Type(0)

        companion object {
            @JvmStatic
            fun from(value: Int): Type {
                return when (value) {
                    0 -> None
                    else -> Unknown
                }
            }

        }
    }

    /**
     * Only 11 Bytes LSB observed
     */
    data class Nonce(val value: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Nonce) return false

            if (!value.contentEquals(other.value)) return false

            return true
        }

        override fun hashCode(): Int {
            return value.contentHashCode()
        }

        companion object {
            fun random(): Nonce {
                return Nonce(UUID.randomUUID().toByteArray())
            }
        }
    }

    /**
     *  0  1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19
     *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     *  |T | Amount                | Nonce                          |
     *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
     */
    fun encode(): ByteArray {
        return with(ByteArrayOutputStream()) {
            write(byteArrayOf(type.value.toByte()))
            write(amount.value.longToByteArray())
            write(nonce.value, 0, 11)
            toByteArray()
        }
    }

    companion object {
        @JvmStatic
        fun decode(bytes: ByteArray): KinCode {
            return with(ByteArrayInputStream(bytes)) {
                val type = Type.from(read())
                val amount = QuarkAmount(read(8).byteArrayToLong())
                val nonce = Nonce(read(11))
                KinCode(type, amount, nonce)
            }
        }
    }
}
