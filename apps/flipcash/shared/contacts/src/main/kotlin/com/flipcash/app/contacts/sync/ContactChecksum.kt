package com.flipcash.app.contacts.sync

import com.getcode.solana.keys.Checksum
import com.getcode.solana.keys.LENGTH_32
import java.security.MessageDigest

object ContactChecksum {
    fun compute(e164s: Set<String>): Checksum = Checksum(computeBytes(e164s).toList())

    fun computeBytes(e164s: Set<String>): ByteArray {
        val xored = ByteArray(LENGTH_32)
        if (e164s.isEmpty()) return xored

        val digest = MessageDigest.getInstance("SHA-256")

        for (e164 in e164s) {
            val hash = digest.digest(e164.toByteArray(Charsets.UTF_8))
            for (i in xored.indices) {
                xored[i] = (xored[i].toInt() xor hash[i].toInt()).toByte()
            }
            digest.reset()
        }

        return xored
    }
}
