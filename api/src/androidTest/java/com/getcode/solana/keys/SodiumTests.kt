package com.getcode.solana.keys

import com.getcode.ed25519.Ed25519
import com.getcode.network.repository.publicKeyFromBytes
import com.getcode.util.testBlocking
import com.getcode.vendor.Base58
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import org.junit.Assert
import org.junit.Test

class SodiumTests {

    @Test
    fun testPrivateToCurve() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val privateKey =
                PrivateKey(base58 = "4vXZTu7W8FKV2cNB7t2MTp8KXrWpJRCodzUPoyPy1MWZiZQqVVXUrycCdoagzPN6YE9w9pyTbZVzVw9iLDUT7adR")

            Assert.assertEquals(
                "F197LA9gxNFgu6bwmHFuBJWU4yuA3wRsBDky9twjeoJr",
                privateKey.curvePrivate.getOrNull()!!.base58(),
            )
        }
    }

    @Test
    fun testPublicToCurve() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val publicKey = PublicKey(base58 = "GV6Aow3jPRXFQiC36EGc1BabhFVY1mEwKPEuwZorGh3R")

            Assert.assertEquals(
                "37asXhXd7c8vUNCxHHxAMMrAGPCpYrAtJ8L1fvu4rxzU",
                publicKey.curvePublic.getOrNull()!!.base58(),
            )
        }
    }

    @Test
    fun testSharedKey() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val privateKey1 =
                PrivateKey(base58 = "2fJLfaTREkNBiDbB26dL4syDozhCEf2pNMorXvBf7593yC59d1kDFsXAA9cN63Bb5MDUgSeU5AhsfS2aTZQHoNyU")
            val privateKey2 =
                PrivateKey(base58 = "3GKRCGo814rSVa6XkFARZGq13Rb7DSGwF2c6SSRSzMfyQ3wuDAPoELzhsvH6r5A1PFACpFuesDaRHUEoL1PFAxRa")

            val publicKey1 = PublicKey(base58 = "eMTkrsg1acVKyk8jp4b6JQM3TK2fSxwaZV3gZqCmxsp")
            val publicKey2 = PublicKey(base58 = "J1uvrtrg42Yw3zA7v7VK1wBahW8XkTLxqsnKksZab9wS")

            val privateCurve1 = privateKey1.curvePrivate.getOrNull()!!
            val privateCurve2 = privateKey2.curvePrivate.getOrNull()!!

            val publicCurve1 = publicKey1.curvePublic.getOrNull()!!
            val publicCurve2 = publicKey2.curvePublic.getOrNull()!!

            val shared1 = PublicKey.shared(publicCurve1, privateCurve2).getOrNull()!!

            Assert.assertEquals(
                "GC1cihUsj3rBqqdzBmWkEejWuv6p3scxPqCEwUBUUdQq",
                shared1.base58()
            )

            val shared2 = PublicKey.shared(publicCurve2, privateCurve1).getOrNull()!!

            Assert.assertEquals(
                "GC1cihUsj3rBqqdzBmWkEejWuv6p3scxPqCEwUBUUdQq",
                shared2.base58()
            )
        }
    }

    @Test
    fun testRoundTrip() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val senderPrivate =
                PrivateKey(base58 = "2tKSW5f1dag1pGzDSsM9yo32KSMNcTkBAvXEfZ1u2pcqkmo8oYcbtsnA8m9YVd8EUzVJeU5mvjFKjPQF2m4Xifg8")
            val senderPublic = PublicKey(base58 = "3hpSY5ibVa87dDLJhLdVAy7QVso2Edhr28ZEJmpDF7UQ")

            val receiverPrivate =
                PrivateKey(base58 = "38EyWg6Eay5bhcZR465FD2agT2bf7BhyWNJJ64ypfdQGTb6mHU3an2f8pvWapSrE3j3hEFu1h7HYoa6eykAHUBJr")
            val receiverPublic = PublicKey(base58 = "6Hsb5k8UjjsowqXgRBr1BR3EKFPeYjA8Nn9prYDU24v6")

            val nonce = Base58.decode("Jc1X8GdaMmcRDRKiAaMZSRBDLZAFuf9xq").toList()
            val expectedEncrypted =
                Base58.decode("2eXsYDo1gcuYc1Nw7uUGZmJZrj2vu33TnrXve62HwzhyTggjjz").toList()

            val message = "super secret message"

            val encrypted = message.boxSeal(
                privateKey = senderPrivate,
                publicKey = receiverPublic,
                nonce = nonce
            )

            Assert.assertEquals(
                expectedEncrypted,
                encrypted.getOrNull()!!,
            )

            val decrypted = encrypted.getOrNull()!!.boxOpen(
                privateKey = receiverPrivate,
                publicKey = senderPublic,
                nonce = nonce,
            )

            Assert.assertEquals(
                message,
                String(decrypted.getOrNull()!!.toByteArray())
            )
        }
    }

    @Test
    fun testRoundTrip2() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val sender = Ed25519.createKeyPair(Base58.decode("BAjtXtzJzjMvF1qHicCQdyi4AC2y9tQMjVCSwNAY5jnz"))
            val receiver = Ed25519.createKeyPair(Base58.decode("BWUXLs1epmgQwc6kf3VuWcX4bkwjiRjGDp3CYNcVDpVd"))

            val nonce = Base58.decode("Jc1X8GdaMmcRDRKiAaMZSRBDLZAFuf9xq").toList()
            val expectedEncrypted =
                Base58.decode("SZa3RhUVBNhuCT8ARoG5k7V7Ji6TtoJfX8JtpZEHyUzMe4EEb").toList()

            val message = "super secret message"

            val encrypted = message.boxSeal(
                privateKey = sender.encryptionPrivateKey!!,
                publicKey = receiver.publicKeyFromBytes,
                nonce = nonce
            )

            Assert.assertEquals(
                expectedEncrypted,
                encrypted.getOrNull()!!,
            )

            val decrypted = encrypted.getOrNull()!!.boxOpen(
                privateKey = receiver.encryptionPrivateKey!!,
                publicKey = sender.publicKeyFromBytes,
                nonce = nonce,
            )

            Assert.assertEquals(
                message,
                String(decrypted.getOrNull()!!.toByteArray())
            )
        }
    }

    @Test
    fun testDecryptRealBlockchainMessage() {
        testBlocking {
            LibsodiumInitializer.initialize()
            val senderPublic = PublicKey(base58 = "McS32C1q6Rv1odkEoR5g1xtFBN7TdbkLFvGeyvQtzLF")
            val receiverKeyPair =
                Ed25519.createKeyPair(Base58.decode("CADTR1JPf4KzQ9fuYJMRaaWbfshB8qSb38RpFzC8mtjq"))

            val nonce = Base58.decode("PjgJtLTPZmHGCqJ6Sj1X4ZN8wVbinW4nU").toList()
            val encrypted =
                Base58.decode("2BRs8n3fqqDUXVjEdup3d5zoxFALbvs6KcKnMCgpoJ6iafXjikwqbjnbehyha")
                    .toList()

            val expectedDecrypted = "Blockchain messaging is 🔥"

            val decrypted = encrypted.boxOpen(
                privateKey = receiverKeyPair.encryptionPrivateKey!!,
                publicKey = senderPublic,
                nonce = nonce
            )

            decrypted.exceptionOrNull()?.printStackTrace()

            Assert.assertEquals(
                expectedDecrypted,
                String(decrypted.getOrNull()!!.toByteArray())
            )
        }
    }
}