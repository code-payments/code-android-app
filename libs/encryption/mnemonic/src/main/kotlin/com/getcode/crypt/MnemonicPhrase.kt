package com.getcode.crypt

import com.getcode.ed25519.Ed25519
import com.getcode.utils.decodeBase64
import com.getcode.utils.encodeBase64
import org.kin.sdk.base.tools.Base58


class MnemonicPhrase(val kind: Kind, val words: List<String>) {

    enum class Kind {
        L12,
        L24
    }

    fun getSolanaKeyPair(path: DerivePath = DerivePath.primary): Ed25519.KeyPair {
        val mnemonicCode = MnemonicCache.cachedCode
        val mnemonicSeed = MnemonicCache.cache[words to (path.password ?: "nopass")]
            ?: MnemonicCode.toSeed(
                words,
                path.password.orEmpty()
            ).also {
                MnemonicCache.cache[words to (path.password ?: "nopass")] = it
            }

        mnemonicCode.check(words)

        return Derive.path(mnemonicSeed, path)
    }

    fun getBase64EncodedEntropy(): String {
        return MnemonicCache.cachedCode
            .toEntropy(words)
            .encodeBase64()
            .replace("\n", "")
    }

    fun getBase58EncodedEntropy(): String {
        return MnemonicCache.cachedCode
            .toEntropy(words)
            .let { Base58.encode(it) }
            .replace("\n", "")
    }

    companion object {

        fun newInstance(words: List<String>): MnemonicPhrase? {
            return when (words.size) {
                12 -> MnemonicPhrase(Kind.L12, words)
                24 -> MnemonicPhrase(Kind.L24, words)
                else -> null
            }
        }

        fun generate(): MnemonicPhrase {
            return fromEntropy(Ed25519.createSeed16())
        }

        fun fromEntropyB64(entropyB64: String): MnemonicPhrase {
            val entropy = entropyB64.decodeBase64()
            return fromEntropy(entropy)
        }

        fun fromEntropyB58(entropyB58: String): MnemonicPhrase {
            val entropy = Base58.decode(entropyB58)
            return fromEntropy(entropy)
        }

        fun fromEntropy(entropy: ByteArray): MnemonicPhrase {
            val words = MnemonicCache.cachedCode.toMnemonic(entropy)
            return newInstance(words)!!
        }
    }
}