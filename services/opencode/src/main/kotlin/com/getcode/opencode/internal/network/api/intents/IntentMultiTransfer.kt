package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * A SendPublicMultiPayment intent: one public transfer from the source PRIMARY account to each of
 * 2..10 OCP-managed PRIMARY destinations. Mirrors [IntentTransfer]'s direct path.
 *
 * Action list emitted, in payment order (ids are assigned 0..n-1 by [ActionGroup]):
 * ```
 * for payment in payments
 *   NoPrivacyTransferAction(source = vault of source cluster, destination = payment.destination,
 *                           authority = source authority, amount = payment quarks, mint)
 * ```
 * Each share's quarks come from that share's own [LocalFiat]; they are not split from a total.
 * All payments share one [VerifiedState] (currency, core mint rate, reserve state).
 */
internal class IntentMultiTransfer(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): OcpTransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    /**
     * @param destination the destination's derived PRIMARY vault for the intent mint
     * @param destinationOwner the destination's owner; always sent so a missing PRIMARY can be auto-opened
     * @param amount this share's amount
     */
    data class Share(
        val destination: PublicKey,
        val destinationOwner: PublicKey,
        val amount: LocalFiat,
    )

    companion object {
        const val MIN_PAYMENTS = 2
        const val MAX_PAYMENTS = 10

        fun create(
            shares: List<Share>,
            mint: Mint,
            sourceCluster: AccountCluster,
            verifiedState: VerifiedState,
            appMetadata: ByteArray? = null,
        ): IntentMultiTransfer {
            require(shares.size in MIN_PAYMENTS..MAX_PAYMENTS) {
                "SendPublicMultiPayment requires $MIN_PAYMENTS..$MAX_PAYMENTS payments, got ${shares.size}"
            }
            require(shares.map { it.destination }.distinct().size == shares.size) {
                "Destinations must be unique"
            }
            require(shares.map { it.destinationOwner }.distinct().size == shares.size) {
                "Destination owners must be unique"
            }
            require(shares.none { it.destination == sourceCluster.vaultPublicKey }) {
                "A destination cannot be the source"
            }
            require(shares.none { it.destinationOwner == sourceCluster.authorityPublicKey }) {
                "A destination owner cannot be the source owner"
            }

            require(shares.all { it.amount.mint == mint }) {
                "Every payment must be denominated in the intent mint"
            }
            val shareCurrencies = shares.map { it.amount.nativeAmount.currencyCode }.distinct()
            require(shareCurrencies.size == 1) {
                "Every payment must use the same currency, got $shareCurrencies"
            }
            val verifiedCurrency = verifiedState.rateProto.exchangeRate.currencyCode
            require(shareCurrencies.single().name.equals(verifiedCurrency, ignoreCase = true)) {
                "Payment currency ${shareCurrencies.single().name} must match the verified rate currency $verifiedCurrency"
            }

            val actionGroup = buildActionGroup {
                shares.forEach { share ->
                    add(
                        ActionPublicTransfer.newInstance(
                            owner = sourceCluster.authority.keyPair,
                            source = sourceCluster.vaultPublicKey,
                            destination = share.destination,
                            amount = share.amount.underlyingTokenAmount,
                            mint = mint,
                        )
                    )
                }
            }

            return IntentMultiTransfer(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.SendPublicMultiPayment(
                    source = sourceCluster.vaultPublicKey,
                    mint = mint,
                    payments = shares.map { share ->
                        val verified = ExchangeData.Verified(
                            mint = mint,
                            quarks = share.amount.underlyingTokenAmount.quarks,
                            nativeAmount = share.amount.nativeAmount.decimalValue,
                            verifiedState = verifiedState,
                        )
                        TransactionMetadata.SendPublicMultiPayment.Payment(
                            destination = share.destination,
                            destinationOwner = share.destinationOwner,
                            verifiedExchangeData = verified,
                        )
                    },
                    appMetadata = appMetadata,
                ),
                actionGroup = actionGroup,
            )
        }
    }
}
