package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.generate
import com.getcode.opencode.internal.network.api.intents.actions.ActionTransfer
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.core.LocalFiat
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentTransfer(
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
                    .setIsWithdrawal(true)
                    .setExchangeData(
                        TransactionService.ExchangeData.newBuilder()
                            .setQuarks(amount.converted.quarks.toLong())
                            .setCurrency(amount.rate.currency.name.lowercase())
                            .setExchangeRate(amount.rate.fx)
                            .setNativeAmount(amount.converted.doubleValue)
                    )
            )
            .build()
    }

    companion object {
        fun create(
            amount: LocalFiat,
            sourceCluster: AccountCluster,
            destination: PublicKey
        ): IntentTransfer {
            val id = PublicKey.generate()

            val transfer = ActionTransfer.newInstance(
                kind = ActionTransfer.Kind.Transfer,
                sourceCluster = sourceCluster,
                destination = destination,
                amount = amount.converted
            )

            return IntentTransfer(
                id = id,
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