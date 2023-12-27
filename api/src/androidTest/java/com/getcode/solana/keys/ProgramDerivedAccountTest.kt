package com.getcode.solana.keys

import com.getcode.model.Kin
import com.getcode.network.repository.decodeBase58
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramDerivedAccountTest {
    @Test
    fun testTimelockDerivation() {
        val owner = PublicKey.fromBase58("BuAprBZugjXG6QRbRQN8QKF8EzbW5SigkDuyR9KtqN5z")
        val derivedAccounts = TimelockDerivedAccounts.newInstance(owner)

        assertEquals(owner.base58(), derivedAccounts.owner.base58())
        assertEquals("7Ema8Z4gAUWegampp2AuX4cvaTRy3VMwJUq8LMJshQTV", derivedAccounts.state.publicKey.base58())
        assertEquals(254, derivedAccounts.state.bump)
        assertEquals("3538bYdWoRXUgBbyAyvG3Zemmawh75nmCQEvWc9DfKFR", derivedAccounts.vault.publicKey.base58())
        assertEquals(255, derivedAccounts.vault.bump)
    }

    @Test
    fun testLegacyTimelockDerivation() {
        val owner = PublicKey.fromBase58(base58 = "8XfsstyiyT4rCY8ydYthXLisgPHHZFXVtJbcRSsebkWo")
        val derivedAccounts = TimelockDerivedAccounts.newInstance(owner = owner, legacy = true)

        assertEquals(owner.base58(), derivedAccounts.owner.base58())
        assertEquals("BsJs1qFrhJU6QZp3yniAkLfECA898a8yTxbJhVsY9rW2", derivedAccounts.state.publicKey.base58())
        assertEquals(254, derivedAccounts.state.bump)
        assertEquals("Aqo1xaEUQqtVLcz2Q6sL5u2YwMaAJygTDeSWf7nEEWWN", derivedAccounts.vault.publicKey.base58())
        assertEquals(250, derivedAccounts.vault.bump)
    }

    @Test
    fun testCommitmentDerivation() {
        val treasury    = PublicKey.fromBase58("3HR2k4etyHtBgHCAisRQ5mAU1x3GxWSgmm1bHsNzvZKS")
        val destination = PublicKey.fromBase58("A1WsiTaL6fPei2xcqDPiVnRDvRwpCjne3votXZmrQe86")
        val recentRoot  = Hash("BvtnzMe2CSunpGoYnvK6YZut1Jg41yaPBDGdJToPQrqy".decodeBase58().toList())
        val transcript  = Hash("91aPsVLa6xCcVfC9FozexaMK8TgKCUZMkj4k6yPy2q4S".decodeBase58().toList())

        val derivedAccounts = SplitterCommitmentAccounts.newInstance(
                treasury = treasury,
                destination = destination,
                recentRoot = recentRoot,
                transcript = transcript,
                amount = Kin.Companion.fromKin(1)
        )

        assertEquals(treasury, derivedAccounts.treasury)
        assertEquals(destination, derivedAccounts.destination)
        assertEquals(recentRoot, derivedAccounts.recentRoot)
        assertEquals(transcript, derivedAccounts.transcript)

        assertEquals("4vF8wWhuUSPTmUWPRvNcB5aPNzDvjCYBhyizpG6VFNi6", derivedAccounts.state.publicKey.base58())
        assertEquals(247, derivedAccounts.state.bump)
        assertEquals("7BXkxmuwH4GGm48gPWMWqHnLYX7NwrtGPUtfHKnhgMmZ", derivedAccounts.vault.publicKey.base58())
        assertEquals(254, derivedAccounts.vault.bump)
    }

    @Test
    fun testTranscriptHash() {
        val transcript = SplitterTranscript(
            intentId = PublicKey.fromBase58(base58 = "4roBdWPCqbuqr4YtPavfi7hTAMdH52RXMDgKhqQ4qvX6"),
            actionId = 1,
            amount = Kin.Companion.fromKin(40),
            source = PublicKey.fromBase58(base58 = "GNVyMgwkFQvm3YLuJdEVW4xEoqDYnixVaxVYT59frGWW"),
            destination = PublicKey.fromBase58(base58 = "Cia66LdCtvfJ6G5jjmLtNoFx5JvWr3uNv2iaFvmSS9gW"),
        )

        assertEquals("5Yh4E953ePoBWe8w78FgMqEjiNmtCQi2ct9BTc2shuLi", transcript.transcriptHash.base58())
    }
}