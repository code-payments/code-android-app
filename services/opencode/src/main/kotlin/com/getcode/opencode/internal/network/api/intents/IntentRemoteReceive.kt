package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

internal class IntentRemoteReceive(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
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
                owner = giftCard.cluster,
                source = giftCard.cluster,
                destination = owner.vaultPublicKey,
                canAutoReturn = false,
            )

            return IntentRemoteReceive(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.ReceivePublicPayment(
                    source = giftCard.cluster.vaultPublicKey,
                    amount = amount,
                    isRemoteSend = true,
                ),
                actionGroup = ActionGroup().apply {
                    actions = listOf(withdrawFromGiftCard)
                }
            )
        }
    }
}