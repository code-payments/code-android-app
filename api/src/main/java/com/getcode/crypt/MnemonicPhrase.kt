package com.getcode.crypt

import android.content.Context
import com.getcode.ed25519.Ed25519
import com.getcode.network.repository.decodeBase64
import com.getcode.network.repository.encodeBase64
import org.kin.sdk.base.tools.Base58


class MnemonicPhrase(val kind: Kind, val words: List<String>) {

    enum class Kind {
        L12,
        L24
    }

    fun getSolanaKeyPair(context: Context, path: DerivePath = DerivePath.primary): Ed25519.KeyPair {
        val mnemonicCode = MnemonicCode(context.resources)
        val mnemonicSeed = MnemonicCode.toSeed(words, path.password.orEmpty())
        mnemonicCode.check(words)

        return Derive.path(mnemonicSeed, path)
    }

    fun getBase64EncodedEntropy(context: Context): String {
        return MnemonicCode(context.resources)
            .toEntropy(words)
            .encodeBase64()
            .replace("\n", "")
    }

    fun getBase58EncodedEntropy(context: Context): String {
        return MnemonicCode(context.resources)
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

        fun generate(context: Context): MnemonicPhrase {
            return fromEntropy(context, Ed25519.createSeed16())
        }

        fun fromEntropyB64(context: Context, entropyB64: String): MnemonicPhrase {
            val entropy = entropyB64.decodeBase64()
            return fromEntropy(context, entropy)
        }

        fun fromEntropyB58(context: Context, entropyB58: String): MnemonicPhrase {
            val entropy = Base58.decode(entropyB58)
            return fromEntropy(context, entropy)
        }

        fun fromEntropy(context: Context, entropy: ByteArray): MnemonicPhrase {
            val words = MnemonicCode(context.resources).toMnemonic(entropy)
            return newInstance(words)!!
        }
    }
}