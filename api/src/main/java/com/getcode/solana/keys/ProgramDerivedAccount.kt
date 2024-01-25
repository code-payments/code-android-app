package com.getcode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.model.Kin
import com.getcode.solana.keys.Key32.Companion.kinMint
import com.getcode.solana.organizer.AccountCluster
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

        fun newInstance(owner: PublicKey, legacy: Boolean = false): TimelockDerivedAccounts {
            val state: ProgramDerivedAccount
            val vault: ProgramDerivedAccount

            if (legacy) {
                state =
                    PublicKey.deriveLegacyTimelockStateAccount(owner = owner, lockout = 1_814_400)
                vault = PublicKey.deriveLegacyTimelockVaultAccount(stateAccount = state.publicKey)
            } else {
                state = PublicKey.deriveTimelockStateAccount(owner = owner, lockout = lockoutInDays)
                vault = PublicKey.deriveTimelockVaultAccount(
                    stateAccount = state.publicKey,
                    version = dataVersion
                )
            }

            return TimelockDerivedAccounts(
                owner = owner,
                state = state,
                vault = vault
            )
        }
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
    companion object {
        fun newInstance(
            treasury: PublicKey,
            destination: PublicKey,
            recentRoot: Hash,
            transcript: Hash,
            amount: Kin
        ): SplitterCommitmentAccounts {
            val state = PublicKey.deriveCommitmentStateAccount(
                treasury = treasury,
                recentRoot = recentRoot,
                transcript = transcript,
                destination = destination,
                amount = amount
            )

            val vault = PublicKey.deriveCommitmentVaultAccount(
                treasury = treasury,
                commitmentState = state.publicKey
            )

            return SplitterCommitmentAccounts(
                treasury = treasury,
                destination = destination,
                recentRoot = recentRoot,
                transcript = transcript,
                state = state,
                vault = vault,
            )
        }

        fun newInstance(
            source: AccountCluster,
            destination: PublicKey,
            amount: Kin,
            treasury: PublicKey,
            recentRoot: Hash,
            intentId: PublicKey,
            actionId: Int
        ): SplitterCommitmentAccounts {
            val transcript = SplitterTranscript(
                intentId = intentId,
                actionId = actionId,
                amount = amount,
                source = source.timelockAccounts.vault.publicKey,
                destination = destination
            )

            return newInstance(
                treasury = treasury,
                destination = destination,
                recentRoot = recentRoot,
                transcript = transcript.transcriptHash,
                amount = amount
            )
        }
    }
}

data class SplitterTranscript(
    val intentId: PublicKey,
    val actionId: Int, val
    amount: Kin,
    val source: PublicKey,
    val destination: PublicKey
) {
    val description =
        "receipt[${intentId.base58()}, $actionId]: " +
                "transfer ${amount.quarks} quarks " +
                "from ${source.base58()} to ${destination.base58()}"

    val transcriptHash: Hash = Hash(Sha256Hash.hash(description.toUTF8Bytes()).toList())
}

data class AssociatedTokenAccount(
    val owner: PublicKey,
    val ata: ProgramDerivedAccount
) {
    companion object {
        fun newInstance(owner: PublicKey): AssociatedTokenAccount {
            return AssociatedTokenAccount(
                owner = owner,
                ata = PublicKey.deriveAssociatedAccount(owner = owner, mint = kinMint)
            )
        }
    }
}

