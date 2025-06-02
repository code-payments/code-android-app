package com.getcode.opencode.model.transactions

import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

data class WithdrawalAvailability(
    val destination: PublicKey,
    val isValid: Boolean,
    val kind: Kind,

    val hasResolvedDestination: Boolean,
    val resolvedDestination: PublicKey,

    /**
     * Token account requires initialization before the withdrawal can occur.
     * Server has chosen not to subsidize the fees. The response is guaranteed
     * to have set is_valid_payment_destination = false in this case.
     *
     */
    val requiresInitialization: Boolean,

    val feeAmount: Fiat?
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
            kind: Kind,
            requiresInitialization: Boolean,
            feeAmount: ExchangeData.WithoutRate?,
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

            val feeCurrencyCode = (CurrencyCode.tryValueOf(feeAmount?.currencyCode) ?: CurrencyCode.USD)

            return WithdrawalAvailability(
                destination = destination,
                isValid = isValid,
                kind = kind,
                hasResolvedDestination = hasResolvedDestination,
                resolvedDestination = resolvedDestination,
                requiresInitialization = if (isValid) false else requiresInitialization,
                feeAmount = feeAmount?.let { Fiat(feeAmount.nativeAmount, feeCurrencyCode) }
            )
        }
    }
}