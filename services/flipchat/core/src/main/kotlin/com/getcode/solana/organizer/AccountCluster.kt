package com.getcode.solana.organizer

import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.extensions.newInstance
import com.getcode.model.toPublicKey
import com.getcode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts


class AccountCluster(
    val index: Int,
    val authority: DerivedKey,
    val derivation: Derivation
) {

    val authorityPublicKey: PublicKey
        get() = authority.keyPair.publicKeyBytes.toPublicKey()

    val vaultPublicKey: PublicKey
        get() = when (derivation) {
            is Derivation.Timelock -> timelock!!.vault.publicKey
            is Derivation.Usdc -> ata!!.ata.publicKey
        }
    sealed interface Kind {
        data object Timelock: Kind
        data object Usdc: Kind
    }

    sealed interface Derivation {
        data class Timelock(val accounts: TimelockDerivedAccounts): Derivation
        data class Usdc(val account: AssociatedTokenAccount): Derivation
    }

    val timelock: TimelockDerivedAccounts?
        get() = (derivation as? Derivation.Timelock)?.accounts

    val ata: AssociatedTokenAccount?
        get() = (derivation as? Derivation.Usdc)?.account

    companion object {
        fun newInstanceLazy(authority: DerivedKey, index: Int = 0, kind: Kind, legacy: Boolean = false): Lazy<AccountCluster> {
            return lazy { newInstance(authority, index, kind, legacy) }
        }

        fun newInstance(authority: DerivedKey, index: Int = 0, kind: Kind, legacy: Boolean = false): AccountCluster {
            return AccountCluster(
                index = index,
                authority = authority,
                derivation = when (kind) {
                    Kind.Timelock -> Derivation.Timelock(
                        TimelockDerivedAccounts.newInstance(
                            owner = PublicKey(authority.keyPair.publicKeyBytes.toList()),
                            legacy = legacy
                        )
                    )
                    Kind.Usdc -> {
                        Derivation.Usdc(
                            AssociatedTokenAccount.newInstance(
                                owner = authority.keyPair.publicKeyBytes.toPublicKey(),
                                mint = Mint.usdc
                            )
                        )
                    }
                },
            )
        }

        fun using(type: AccountType, index: Int, mnemonic: MnemonicPhrase): AccountCluster {
            return newInstance(
                index = index,
                authority = DerivedKey.derive(
                    path = type.getDerivationPath(index),
                    mnemonic = mnemonic
                ),
                kind = Kind.Timelock
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountCluster

        if (authority != other.authority) return false
        if (derivation != other.derivation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authority.hashCode()
        result = 31 * result + derivation.hashCode()
        return result
    }

}