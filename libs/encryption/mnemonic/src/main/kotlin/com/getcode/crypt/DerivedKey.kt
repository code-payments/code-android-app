package com.getcode.crypt

import com.getcode.ed25519.Ed25519

data class DerivedKey(val path: DerivePath, val keyPair: Ed25519.KeyPair) {

    companion object {
        fun derive(path: DerivePath, mnemonic: MnemonicPhrase): DerivedKey {
            return DerivedKey(
                path,
                mnemonic.getSolanaKeyPair(path)
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DerivedKey

        if (path != other.path) return false
        if (keyPair != other.keyPair) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + keyPair.hashCode()
        return result
    }

}