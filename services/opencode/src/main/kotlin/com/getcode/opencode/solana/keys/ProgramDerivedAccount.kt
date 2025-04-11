package com.getcode.opencode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import org.kin.sdk.base.models.toUTF8Bytes

data class ProgramDerivedAccount(val publicKey: PublicKey, val bump: Int)

class TimelockDerivedAccounts(
    val owner: PublicKey,
    val state: ProgramDerivedAccount,
    val vault: ProgramDerivedAccount
) {
    companion object {
        const val lockoutInDays: Long = 21
        const val dataVersion: Long = 3
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TimelockDerivedAccounts

        if (owner != other.owner) return false
        if (state != other.state) return false
        if (vault != other.vault) return false

        return true
    }

    override fun hashCode(): Int {
        var result = owner.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + vault.hashCode()
        return result
    }
}

class SplitterCommitmentAccounts(
    val treasury: PublicKey,
    val destination: PublicKey,
    val recentRoot: Hash,
    val transcript: Hash,

    val state: ProgramDerivedAccount,
    val vault: ProgramDerivedAccount,
) {
    companion object
}

data class SplitterTranscript(
    val intentId: PublicKey,
    val actionId: Int, val
    amount: Fiat,
    val source: PublicKey,
    val destination: PublicKey
) {
    val description =
        "receipt[${intentId.base58()}, $actionId]: " +
                "transfer ${amount.quarks} quarks " +
                "from ${source.base58()} to ${destination.base58()}"

    val transcriptHash: Hash =
        Hash(Sha256Hash.hash(description.toUTF8Bytes()).toList())
}

 data class AssociatedTokenAccount(
    val owner: PublicKey,
    val ata: ProgramDerivedAccount,
) {
    companion object
}

data class PreSwapStateAccount(
    val owner: PublicKey,
    val state: ProgramDerivedAccount,
) {
    companion object
}

