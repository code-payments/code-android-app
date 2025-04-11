package com.getcode.opencode.model.transactions

import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

data class WithdrawalAvailability(
    val destination: PublicKey,
    val isValid: Boolean,
    val kind: Kind,

    val hasResolvedDestination: Boolean,
    val resolvedDestination: PublicKey
) {
    enum class Kind {
        Unknown,
        TokenAccount,
        OwnerAccount;

        companion object {
            fun tryValueOf(value: String): Kind? {
                return try {
                    valueOf(value)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    companion object {
        fun newInstance(
            destination: PublicKey,
            isValid: Boolean,
            kind: Kind
        ): WithdrawalAvailability {
            val hasResolvedDestination: Boolean
            val resolvedDestination: PublicKey

            when (kind) {
                Kind.Unknown, Kind.TokenAccount -> {
                    hasResolvedDestination = false
                    resolvedDestination = destination
                }

                Kind.OwnerAccount -> {
                    hasResolvedDestination = true
                    resolvedDestination =
                        AssociatedTokenAccount.newInstance(
                            owner = destination,
                            mint = Mint.usdc
                        ).ata.publicKey
                }
            }

            return WithdrawalAvailability(
                destination = destination,
                isValid = isValid,
                kind = kind,
                hasResolvedDestination = hasResolvedDestination,
                resolvedDestination = resolvedDestination
            )
        }
    }
}