package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asExchangeData
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentWithdraw(
    override val id: PublicKey,
    private val sourceCluster: AccountCluster,
    private val destination: PublicKey,
    private val amount: LocalFiat,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setSource(sourceCluster.vaultPublicKey.asSolanaAccountId())
                    .setDestination(destination.asSolanaAccountId())
                    .setIsRemoteSend(false)
                    .setIsWithdrawal(true)
                    .setExchangeData(amount.asExchangeData())
            )
            .build()
    }

    companion object {
        fun create(
            amount: LocalFiat,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            rendezvous: PublicKey,
        ): IntentWithdraw {
            val transfer = ActionPublicTransfer.newInstance(
                sourceCluster = sourceCluster,
                destination = destination,
                amount = amount.usdc
            )

            return IntentWithdraw(
                id = rendezvous,
                sourceCluster = sourceCluster,
                destination = destination,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                }
            )
        }
    }
}

sealed class IntentPublicTransferException: Exception() {
    class BalanceMismatchException: IntentPublicTransferException()
}