package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.generate
import com.getcode.opencode.internal.network.api.intents.actions.ActionWithdraw
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentCancelRemoteSend(
    override val id: PublicKey,
    private val amount: LocalFiat,
    private val giftCard: GiftCardAccount,
    override val actionGroup: ActionGroup
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setReceivePaymentsPublicly(
                TransactionService.ReceivePaymentsPubliclyMetadata.newBuilder()
                    .setIsRemoteSend(true)
                    .setIsIssuerVoidingGiftCard(true)
                    .setQuarks(amount.usdc.quarks.toLong())
                    .setSource(giftCard.cluster.vaultPublicKey.asSolanaAccountId())
            ).build()
    }

    internal companion object {
        fun create(
            giftCard: GiftCardAccount,
            amount: LocalFiat,
            owner: AccountCluster,
        ): IntentCancelRemoteSend {
            val intentId = PublicKey.generate()

            val withdrawFromGiftCard = ActionWithdraw.newInstance(
                amount = amount.converted,
                sourceCluster = giftCard.cluster,
                destination = owner.vaultPublicKey
            )

            return IntentCancelRemoteSend(
                id = intentId,
                amount = amount,
                giftCard = giftCard,
                actionGroup = ActionGroup().apply {
                    actions = listOf(withdrawFromGiftCard)
                }
            )
        }
    }
}