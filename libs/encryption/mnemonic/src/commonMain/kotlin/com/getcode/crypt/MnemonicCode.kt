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

/**
 * Converts between binary seed values and lists of words per the
 * [BIP 39 specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki).
 */
object MnemonicCode {

    private val wordList: List<String> = Bip39EnglishWordList.words

    /** UNIX time for when the BIP39 standard was finalised. Use as a default seed birthday. */
    var BIP39_STANDARDISATION_TIME_SECS: Long = 1381276800L

    private const val PBKDF2_ROUNDS = 2048

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

    /** Converts a mnemonic word list to a 64-byte PBKDF2-SHA512 seed. */
    fun toSeed(words: List<String>, passphrase: String): ByteArray {
        val pass = words.joinToString(" ")
        val salt = "mnemonic$passphrase"
        return PBKDF2SHA512.derive(pass, salt, PBKDF2_ROUNDS, 64)
    }

    private fun bytesToBits(data: ByteArray): BooleanArray {
        val bits = BooleanArray(data.size * 8)
        for (i in data.indices)
            for (j in 0 until 8)
                bits[i * 8 + j] = (data[i].toInt() and (1 shl (7 - j))) != 0
        return bits
    }
}
