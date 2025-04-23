package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.api.intents.actions.ActionTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionWithdraw
import com.getcode.opencode.internal.network.extensions.asExchangeData
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentRemoteSend(
    override val id: PublicKey,
    private val sourceCluster: AccountCluster,
    private val amount: LocalFiat,
    internal val giftCardAccount: GiftCardAccount,
    override val actionGroup: ActionGroup
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setIsRemoteSend(true)
                    .setIsWithdrawal(false)
                    .setSource(sourceCluster.vaultPublicKey.asSolanaAccountId())
                    .setDestination(giftCardAccount.cluster.vaultPublicKey.asSolanaAccountId())
                    .setExchangeData(amount.asExchangeData())
            )
            .build()
    }

    internal companion object {
        fun create(
            rendezvous: PublicKey,
            sourceCluster: AccountCluster,
            giftCard: GiftCardAccount,
            amount: LocalFiat,
        ): IntentRemoteSend {
            // 1. Open gift card account
            val openGiftCardAccount = ActionOpenAccount.createGiftCard(giftCard.cluster)

            // 2. Move all funds from primary into the outgoing account and prepare for transfer
            val transferToGiftCardAccount = ActionTransfer.newInstance(
                amount = amount.converted,
                sourceCluster = sourceCluster,
                destination = openGiftCardAccount.owner.vaultPublicKey
            )

            // 3. Transfer all collected funds to the destination account
            val withdrawToDestination = ActionWithdraw.newInstance(
                amount = amount.converted,
                sourceCluster = sourceCluster,
                destination = giftCard.cluster.vaultPublicKey
            )

            val actions = ActionGroup().apply {
                actions = listOf(
                    openGiftCardAccount,
                    transferToGiftCardAccount,
                    withdrawToDestination
                )
            }

            return IntentRemoteSend(
                id = rendezvous,
                sourceCluster = sourceCluster,
                giftCardAccount = giftCard,
                amount = amount,
                actionGroup = actions
            )
        }
    }
}