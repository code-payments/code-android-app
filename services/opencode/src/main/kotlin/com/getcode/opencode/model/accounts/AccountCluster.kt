package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivedKey
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.internal.extensions.toPublicKey
import com.getcode.opencode.internal.solana.extensions.deriveDepositAccount
import com.getcode.opencode.internal.solana.extensions.deriveVirtualMachineAccount
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

class AccountCluster(
    val authority: DerivedKey,
    val timelock: TimelockDerivedAccounts,
) {
    val rendezvous: Ed25519.KeyPair
        get() = authority.keyPair

    val authorityPublicKey: PublicKey
        get() = authority.keyPair.publicKeyBytes.toPublicKey()

    val vaultPublicKey: PublicKey
        get() = timelock.vault.publicKey

    val usdcDepositAddress: PublicKey
        get() = depositAddressFor(Token.usdc)

    fun depositAddressFor(token: Token): PublicKey = deriveDepositAddressFor(authorityPublicKey, token)

    fun withTimelockForToken(token: Token): AccountCluster = newInstance(authority, token)

    companion object {
        fun newInstance(authority: DerivedKey, token: Token = Token.usdc): AccountCluster {
            val timelock = TimelockDerivedAccounts.newInstance(
                owner = authority.keyPair.toPublicKey(),
                token = token
            )

            return AccountCluster(
                authority = authority,
                timelock = timelock,
            )
        }
    }

    private fun deriveDepositAddressFor(depositor: PublicKey, token: Token): PublicKey {
        val vmAddress = PublicKey.deriveVirtualMachineAccount(
            mint = token.address,
            authority = token.vmMetadata.authority,
            lockout = token.vmMetadata.lockDurationInDays.toUByte(),
        )

        return PublicKey.deriveDepositAccount(vmAddress.publicKey, depositor).publicKey
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