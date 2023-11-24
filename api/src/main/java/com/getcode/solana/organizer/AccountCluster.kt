package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.TimelockDerivedAccounts


class AccountCluster(
    val index: Int,
    val authority: DerivedKey,
    val timelockAccounts: TimelockDerivedAccounts
) {
    companion object {
        fun newInstanceLazy(authority: DerivedKey, index: Int = 0, legacy: Boolean = false): Lazy<AccountCluster> {
            return lazy { newInstance(authority, index, legacy) }
        }

        fun newInstance(authority: DerivedKey, index: Int = 0, legacy: Boolean = false): AccountCluster {
            return AccountCluster(
                index = index,
                authority = authority,
                timelockAccounts = TimelockDerivedAccounts.newInstance(
                    owner = PublicKey(authority.keyPair.publicKeyBytes.toList()),
                    legacy = legacy
                )
            )
        }

        fun using(context: Context, type: AccountType, index: Int, mnemonic: MnemonicPhrase): AccountCluster {
            return newInstance(
                index = index,
                authority = DerivedKey.derive(
                    context = context,
                    path = type.getDerivationPath(index),
                    mnemonic = mnemonic
                )
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountCluster

        if (authority != other.authority) return false
        if (timelockAccounts != other.timelockAccounts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = authority.hashCode()
        result = 31 * result + timelockAccounts.hashCode()
        return result
    }

}