package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionFeePayment
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asExchangeData
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.minus
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

internal class IntentWithdraw(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    companion object {
        fun create(
            amount: LocalFiat,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            destinationOwner: PublicKey?,
            fee: Fee? = null,
        ): IntentWithdraw {
            // transfer the amount less any fee
            val transferAmount = amount.usdc - (fee?.fiat ?: Fiat.Zero)

            val actionGroup = buildActionGroup {
                add(
                    ActionPublicTransfer.newInstance(
                        owner = sourceCluster.authority.keyPair,
                        source = sourceCluster.vaultPublicKey,
                        destination = destination,
                        amount = transferAmount,
                    )
                )

                if (fee != null) {
                    add(
                        ActionFeePayment.newInstance(
                            fee = fee,
                            source = sourceCluster,
                        )
                    )
                }
            }

            return IntentWithdraw(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    destinationOwner = destinationOwner,
                    amount = amount,
                    isRemoteSend = false,
                    isWithdrawal = true,
                ),
                actionGroup = actionGroup
            )
        }
    }
}

sealed class IntentPublicTransferException : Exception() {
    class BalanceMismatchException : IntentPublicTransferException()
}