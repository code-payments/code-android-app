package com.getcode.opencode.model.transactions

import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.solana.keys.PublicKey

sealed interface TransactionMetadata {
    /**
     * Open a set of accounts. Currently, clients should only use this for new users
     * to open all required accounts up front (buckets, incoming, and outgoing).
     *
     * ```
     * Action Specification:
     *
     * for account in [PRIMARY]
     *   actions.push_back(OpenAccountAction(account))
     * ```
     */
    data class OpenAccount(val type: AccountType): TransactionMetadata

    sealed interface PublicPayment {
        val source: PublicKey
        val exchangeData: ExchangeData.WithRate
    }

    /**
     * Send a payment to a destination account publicly.
     *
     * <p>
     *
     * ```
     * Action Specification:
     *
     * actions = [NoPrivacyTransferAction(PRIMARY, destination, ExchangeData.Quarks)]
     * ```
     *
     * @param source The primary account where funds will be sent from. The primary account is assumed if this
     * field is not set for backwards compatibility with old clients.
     * @param destination The destination token account to send funds to. This cannot be a Code
     * temporary account.
     * @param exchangeData The exchange data of total funds being sent to the destination
     * @param isRemoteSend Is the payment a remote send?
     * @param isWithdrawal Is the payment a withdrawal?
     */
    data class SendPublicPayment(
        override val source: PublicKey,
        val destination: PublicKey,
        val destinationOwner: PublicKey? = null,
        override val exchangeData: ExchangeData.WithRate,
        val isRemoteSend: Boolean,
        val isWithdrawal: Boolean
    ): TransactionMetadata, PublicPayment {
        constructor(
            source: PublicKey,
            destination: PublicKey,
            destinationOwner: PublicKey? = null,
            amount: LocalFiat,
            isRemoteSend: Boolean,
            isWithdrawal: Boolean,
        ) : this(
            source,
            destination,
            destinationOwner,
            ExchangeData.WithRate(
                currencyCode = amount.rate.currency.name,
                exchangeRate = amount.rate.fx,
                nativeAmount = amount.converted.doubleValue,
                quarks = amount.usdc.quarks
            ),
            isRemoteSend,
            isWithdrawal
        )
    }

    /**
     * Receives funds into a user-owned account publicly. All use cases of this intent close the
     * account, requiring all funds to be moved. Use this intent to receive payments from an account
     * not owned by a user's 12 words into a temporary incoming account, ensuring privacy upgradeability.
     *
     * <p>
     *
     * ```
     * Action Specification (Remote Send):
     * actions = [NoPrivacyWithdrawAction(REMOTE_SEND_GIFT_CARD, TEMPORARY_INCOMING[latest_index], quarks)]
     * ```
     *
     * <p>
     *
     * @param source The remote send gift card to receive funds from
     * @param quarks The exact amount of Kin in quarks being received
     * @param isRemoteSend Is the receipt of funds from a remote send gift card? Currently, this is
     * the only use case for this intent and validation enforces the flag to true.
     * @param exchangeData If [isRemoteSend] is true, the original exchange data that was provided as
     * part of creating the gift card account. This is purely a server-provided value.
     * SubmitIntent will disallow this being set.
     */
    data class ReceivePublicPayment(
        override val source: PublicKey,
        val quarks: Long,
        val isRemoteSend: Boolean,
        override val exchangeData: ExchangeData.WithRate,
    ): TransactionMetadata, PublicPayment {
        constructor(
            source: PublicKey,
            amount: LocalFiat,
            isRemoteSend: Boolean,
        ) : this(
            source,
            amount.usdc.quarks,
            isRemoteSend,
            ExchangeData.WithRate(
                currencyCode = amount.rate.currency.name,
                exchangeRate = amount.rate.fx,
                nativeAmount = amount.converted.doubleValue,
                quarks = amount.usdc.quarks
            )
        )
    }

    /**
     * Distribute funds from a pool account publicly to one or more user-owned accounts.
     *
     * <p>
     *
     * ```
     * Action Spec:
     * for distribution in distributions[:len(distributions)-1]
     *   actions.push_back(NoPrivacyTransferAction(POOL, distribution.destination, distributions.quarks))
     * actions.push_back(NoPrivacyWithdrawAction(POOL, distributions[:len(distributions)-1].destination, distributions[:len(distributions)-1].quarks))
     * ```
     * </p>
     *
     * Notes:
     * - All funds must distributed. The balance of the pool must be zero at the end of the intent
     * - The pool is closed at the end of the intent via a NoPrivacyWithdrawAction
     *
     * @param source The pool account to distribute funds from
     * @param distributions The list of distributions to perform
     */
    data class PublicDistribution(
        val source: PublicKey,
        val distributions: List<Distribution>
    ): TransactionMetadata

    data object Unknown: TransactionMetadata
}
