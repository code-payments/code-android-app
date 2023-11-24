package com.getcode.solana

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58

data class AccountMeta(
    var publicKey: PublicKey,
    var isSigner: Boolean,
    var isWritable: Boolean,
    var isPayer: Boolean,
    var isProgram: Boolean,
): Comparable<AccountMeta> {
    fun description(): String {
        val payer = if (isPayer) "p" else ""
        val signer = if (isSigner) "s" else ""
        val writable = if (isWritable) "w" else ""
        return "[$payer $signer $writable ${publicKey.base58()}]"
    }

    companion object {
        fun payer(publicKey: PublicKey): AccountMeta {
            return AccountMeta(
                publicKey = publicKey,
                isSigner = true,
                isWritable = true,
                isPayer = true,
                isProgram = false
            )
        }

        fun writable(publicKey: PublicKey, signer: Boolean = false): AccountMeta {
            return AccountMeta(
                publicKey = publicKey,
                isSigner = signer,
                isWritable = true,
                isPayer = false,
                isProgram = false
            )
        }

        fun readonly(publicKey: PublicKey, signer: Boolean = false): AccountMeta {
            return AccountMeta(
                publicKey = publicKey,
                isSigner = signer,
                isWritable = false,
                isPayer = false,
                isProgram = false
            )
        }

        fun program(publicKey: PublicKey): AccountMeta {
            return AccountMeta(
                publicKey = publicKey,
                isSigner = false,
                isWritable = false,
                isPayer = false,
                isProgram = true
            )
        }

        fun compareLexicographically(left: ByteArray, right: ByteArray): Int {
            var i = 0
            var j = 0
            while (i < left.size && j < right.size) {
                val a: Int = left[i].toInt() and 0xff
                val b: Int = right[j].toInt() and 0xff
                if (a != b) {
                    return a - b
                }
                i++
                j++
            }
            return left.size - right.size
        }
    }

    override fun compareTo(other: AccountMeta): Int {
        fun boolToInt(value: Boolean) = if (value) -1 else 1
        if (this == other) return 0


        if (this.isPayer != other.isPayer) {
            return boolToInt(this.isPayer)
        }

//        if (this.isProgram != other.isProgram) {
//            return boolToInt(!this.isProgram)
//        }

        if (this.isSigner != other.isSigner) {
            return boolToInt(this.isSigner)
        }

        if (this.isWritable != other.isWritable) {
            return boolToInt(this.isWritable)
        }

        if (this.isProgram != other.isProgram) {
            return boolToInt(!this.isProgram)
        }

        return compareLexicographically(this.publicKey.byteArray, other.publicKey.byteArray)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountMeta

        if (publicKey != other.publicKey) return false
        if (isSigner != other.isSigner) return false
        if (isWritable != other.isWritable) return false
        if (isPayer != other.isPayer) return false
        if (isProgram != other.isProgram) return false

        return true
    }

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + isSigner.hashCode()
        result = 31 * result + isWritable.hashCode()
        result = 31 * result + isPayer.hashCode()
        result = 31 * result + isProgram.hashCode()
        return result
    }
}

fun List<AccountMeta>.filterUniqueAccounts(): List<AccountMeta> {
    val container = mutableListOf<AccountMeta>()
    this.forEach { account ->
        var found = false
        var index = 0

        for (existingAccount in container) {
            if (account.publicKey == existingAccount.publicKey) {
                val updatedAccount = existingAccount

                // Promote the existing account to writable if applicable
                if (account.isSigner) {
                    updatedAccount.isSigner = true
                }

                if (account.isWritable) {
                    updatedAccount.isWritable = true
                }

                if (account.isPayer) {
                    updatedAccount.isPayer = true
                }

                container[index] = updatedAccount
                found = true
                break
            }
            index++
        }

        if (!found) {
            container.add(account)
        }
    }

    return container
}