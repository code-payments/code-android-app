package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.internal.extensions.generate
import com.getcode.solana.keys.PublicKey

internal class IntentRemoteReceive(
    override val id: PublicKey,
    private val giftCard: GiftCardAccount,
    private val amount: LocalFiat,
    override val actionGroup: ActionGroup
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setReceivePaymentsPublicly(
                TransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setIsRemoteSend(true)
                    .setQuarks(amount.usdc.quarks.toLong())
                    .setSource(giftCard.cluster.vaultPublicKey.asSolanaAccountId())
            )
            .build()
    }

    internal companion object {
        fun create(
            giftCard: GiftCardAccount,
            owner: AccountCluster,
            amount: LocalFiat,
        ): IntentRemoteReceive {

            // 1. Move all funds from the gift card to the primary account
            val withdrawFromGiftCard = ActionPublicWithdraw.newInstance(
                amount = amount.usdc,
                sourceCluster = giftCard.cluster,
                destination = owner.vaultPublicKey,
                canAutoReturn = false,
            )

            return IntentRemoteReceive(
                id = PublicKey.generate(),
                giftCard = giftCard,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(withdrawFromGiftCard)
                }
            )
        }
    }
}