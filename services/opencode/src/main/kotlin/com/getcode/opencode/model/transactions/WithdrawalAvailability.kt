package com.getcode.opencode.model.transactions

import com.getcode.opencode.internal.solana.extensions.newInstance
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.keys.AssociatedTokenAccount
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * Availability result of a withdrawal to be made.
 */
data class WithdrawalAvailability(
    /**
     * The destination address provided by the user for availability checks.
     */
    val destination: PublicKey,

    /**
     * Server-controlled flag to indicate if the account can be withdrawn to.
     * There are several reasons server may deny it, including:
     *  - Wrong type of Code account
     *  - Unsupported external account type (eg. token account but of the wrong mint)
     *
     * This is guaranteed to be false when account_type = Unknown.
     */
    val isValid: Boolean,

    /**
     * Metadata so the client knows how to withdraw to the account. Server cannot
     * provide precalculated addresses in this response to maintain non-custodial
     * status.
     */
    val kind: Kind,

    /**
     * Whether the server has resolved the destination address as an owner account.
     */
    val hasResolvedDestination: Boolean,

    /**
     * The resolved destination address. If the destination is an owner account, this
     * is the associated token account, otherwise it is the same as the destination.
     */
    val resolvedDestination: PublicKey,

    /**
     * ATA requires initialization before the withdrawal can occur. Server will not
     * subsidize the account creation, so a fee is required.
     */
    val requiresInitialization: Boolean,

    /**
     *
     * The WITHDRAWAL_CREATE_ON_SEND fee, in USD, that must be paid in order to
     * submit a withdrawal to subsidize the creation of the account at time of
     * send. The user must explicitly agree to this fee amount before submitting
     * the intent.
     *
     * This will be set when requires_initialization = true
     *
     */
    val feeAmount: Fiat?
) {
    enum class Kind {
        /**
         * Server cannot determine
         */
        Unknown,

        /**
         * Client uses the address as is in SubmitIntent
         */
        TokenAccount,

        /**
         * Client locally derives the ATA to use in SubmitIntent
         */
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
            mint: Mint,
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
                            mint = mint,
                        ).ata.publicKey
                }
            }

            val feeCurrencyCode =
                (CurrencyCode.tryValueOf(feeAmount?.currencyCode) ?: CurrencyCode.USD)

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