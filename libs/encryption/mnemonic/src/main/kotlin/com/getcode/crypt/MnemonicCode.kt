package com.getcode.crypt

/*
 * Copyright 2013 Ken Sedgwick
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.res.Resources
import com.getcode.encryption.mnemonic.R
import com.getcode.utils.Utils
import com.google.common.base.Stopwatch
import timber.log.Timber
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest

/**
 * Converts between binary seed values and lists of words per the
 * [BIP 39 specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 */
class MnemonicCode {

    var wordList: ArrayList<String> = ArrayList()
        private set

    /**
     * Initialises from the included word list (requires an [android.content.res.Resources]
     * instance to open the raw resource; not usable on bare JVM).
     */
    @Throws(IOException::class)
    constructor(resources: Resources?) {
        val stream = openDefaultWords(resources) ?: return
        init(stream, BIP39_ENGLISH_SHA256)
    }

    /**
     * Initialises with words read from [wordstream]. If [wordListDigest] is non-null the
     * SHA-256 hex digest of the word list is verified against it.
     */
    @Throws(IOException::class, IllegalArgumentException::class)
    constructor(wordstream: InputStream?, wordListDigest: String?) {
        wordstream ?: return
        init(wordstream, wordListDigest)
    }

    @Throws(IOException::class)
    private fun init(wordstream: InputStream, wordListDigest: String?) {
        val br = BufferedReader(InputStreamReader(wordstream, Charsets.UTF_8))
        val list = ArrayList<String>(2048)
        val md: MessageDigest = MessageDigest.getInstance("SHA-256")
        br.forEachLine { word ->
            md.update(word.toByteArray())
            list.add(word)
        }
        br.close()

        if (list.size != 2048) throw IllegalArgumentException("input stream did not contain 2048 words")

        if (wordListDigest != null) {
            val hexDigest = md.digest().toHexString()
            if (hexDigest != wordListDigest) throw IllegalArgumentException("wordlist digest mismatch")
        }

        wordList = list
    }

    /** Returns the word list this instance uses. */
    fun getWordList(): List<String> = wordList

    /**
     * Converts mnemonic word list to original entropy bytes.
     *
     * @throws MnemonicException.MnemonicLengthException if the word count is not a multiple of 3 or the list is empty
     * @throws MnemonicException.MnemonicWordException if a word is not in the word list
     * @throws MnemonicException.MnemonicChecksumException if the checksum does not match
     */
    @Throws(MnemonicException::class)
    fun toEntropy(words: List<String>): ByteArray {
        if (words.size % 3 != 0)
            throw MnemonicException.MnemonicLengthException("Word list size must be multiple of three words.")
        if (words.isEmpty())
            throw MnemonicException.MnemonicLengthException("Word list is empty.")

        val concatLenBits = words.size * 11
        val concatBits = BooleanArray(concatLenBits)
        var wordIndex = 0
        for (word in words) {
            val ndx = wordList.binarySearch(word)
            if (ndx < 0) throw MnemonicException.MnemonicWordException(word)
            for (ii in 0 until 11)
                concatBits[wordIndex * 11 + ii] = (ndx and (1 shl (10 - ii))) != 0
            wordIndex++
        }

        val checksumLengthBits = concatLenBits / 33
        val entropyLengthBits = concatLenBits - checksumLengthBits

        val entropy = ByteArray(entropyLengthBits / 8)
        for (ii in entropy.indices)
            for (jj in 0 until 8)
                if (concatBits[ii * 8 + jj])
                    entropy[ii] = (entropy[ii].toInt() or (1 shl (7 - jj))).toByte()

        val hash = Sha256Hash.hash(entropy)
        val hashBits = bytesToBits(hash)

        for (i in 0 until checksumLengthBits)
            if (concatBits[entropyLengthBits + i] != hashBits[i])
                throw MnemonicException.MnemonicChecksumException()

        return entropy
    }

    /**
     * Converts entropy bytes to a mnemonic word list.
     *
     * @throws MnemonicException.MnemonicLengthException if the entropy length is not a multiple of 4 or is empty
     */
    @Throws(MnemonicException.MnemonicLengthException::class)
    fun toMnemonic(entropy: ByteArray): List<String> {
        if (entropy.size % 4 != 0)
            throw MnemonicException.MnemonicLengthException("Entropy length not multiple of 32 bits.")
        if (entropy.isEmpty())
            throw MnemonicException.MnemonicLengthException("Entropy is empty.")

        val hash = Sha256Hash.hash(entropy)
        val hashBits = bytesToBits(hash)
        val entropyBits = bytesToBits(entropy)
        val checksumLengthBits = entropyBits.size / 32

        val concatBits = BooleanArray(entropyBits.size + checksumLengthBits)
        entropyBits.copyInto(concatBits, destinationOffset = 0)
        hashBits.copyInto(concatBits, destinationOffset = entropyBits.size, endIndex = checksumLengthBits)

        val words = ArrayList<String>()
        val nwords = concatBits.size / 11
        for (i in 0 until nwords) {
            var index = 0
            for (j in 0 until 11) {
                index = index shl 1
                if (concatBits[i * 11 + j]) index = index or 0x1
            }
            words.add(wordList[index])
        }
        return words
    }

    /**
     * Checks whether a mnemonic word list is valid.
     *
     * @throws MnemonicException if validation fails
     */
    @Throws(MnemonicException::class)
    fun check(words: List<String>) {
        toEntropy(words)
    }

    companion object {
        const val TAG: String = "MnemonicCode"

        private const val BIP39_ENGLISH_RESOURCE_NAME = "english.txt"
        private const val BIP39_ENGLISH_SHA256 = "ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db"

        /** UNIX time for when the BIP39 standard was finalised. Use as a default seed birthday. */
        @JvmField
        var BIP39_STANDARDISATION_TIME_SECS: Long = 1381276800L

        private const val PBKDF2_ROUNDS = 2048

        /** Shared instance (null until set, e.g. via [MnemonicCache.init]). */
        @JvmField
        var INSTANCE: MnemonicCode? = null

        init {
            try {
                INSTANCE = MnemonicCode(null as Resources?)
            } catch (e: FileNotFoundException) {
                if (!Utils.isAndroidRuntime()) Timber.e("Could not find word list")
            } catch (e: IOException) {
                Timber.e("Failed to load word list")
            }
        }

        /** Converts a mnemonic word list to a 64-byte PBKDF2-SHA512 seed. */
        @JvmStatic
        fun toSeed(words: List<String>, passphrase: String): ByteArray {
            requireNotNull(passphrase) { "A null passphrase is not allowed." }
            val pass = words.joinToString(" ")
            val salt = "mnemonic$passphrase"
            val watch = Stopwatch.createStarted()
            val seed = PBKDF2SHA512.derive(pass, salt, PBKDF2_ROUNDS, 64)
            watch.stop()
            Timber.i("PBKDF2 took {} %s", watch)
            return seed
        }

        private fun openDefaultWords(resources: Resources?): InputStream? {
            resources ?: return null
            val stream = resources.openRawResource(R.raw.english)
                ?: throw FileNotFoundException(BIP39_ENGLISH_RESOURCE_NAME)
            return stream
        }

        private fun bytesToBits(data: ByteArray): BooleanArray {
            val bits = BooleanArray(data.size * 8)
            for (i in data.indices)
                for (j in 0 until 8)
                    bits[i * 8 + j] = (data[i].toInt() and (1 shl (7 - j))) != 0
            return bits
        }

        private fun ByteArray.toHexString(): String {
            val sb = StringBuilder(size * 2)
            for (b in this) {
                val v = b.toInt() and 0xFF
                sb.append("0123456789abcdef"[v ushr 4])
                sb.append("0123456789abcdef"[v and 0x0F])
            }
            return sb.toString()
        }
    }
}
