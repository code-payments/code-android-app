package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivedKey
import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.PublicKey

class AccountCluster(
    val authority: DerivedKey,
    val timelock: TimelockDerivedAccounts
) {
    val authorityPublicKey: PublicKey
        get() = authority.keyPair.publicKeyBytes.toPublicKey()

    val vaultPublicKey: PublicKey
        get() = timelock.vault.publicKey

    companion object {
        fun newInstance(authority: DerivedKey): AccountCluster {
            return AccountCluster(
                authority = authority,
                timelock = TimelockDerivedAccounts.newInstance(authority.keyPair.toPublicKey())
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AccountCluster) return false
        return authority == other.authority && timelock == other.timelock
    }

    override fun hashCode(): Int {
        return 31 * authority.hashCode() + timelock.hashCode()
    }

}