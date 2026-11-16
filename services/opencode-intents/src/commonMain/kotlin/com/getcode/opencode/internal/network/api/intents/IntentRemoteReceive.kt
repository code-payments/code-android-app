package com.getcode.opencode.internal.network.api.intents

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi
class IntentRemoteReceive(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup
): IntentType() {

    companion object {
        fun create(
            giftCard: GiftCardAccount,
            owner: AccountCluster,
            amount: LocalFiat,
            mint: Mint,
            intentId: PublicKey? = null,
        ): IntentRemoteReceive {

            // 1. Move all funds from the gift card to the primary account
            val withdrawFromGiftCard = ActionPublicWithdraw.newInstance(
                amount = amount.underlyingTokenAmount,
                owner = giftCard.cluster,
                source = giftCard.cluster,
                destination = owner.vaultPublicKey,
                canAutoReturn = false,
                mint = mint,
            )

            return IntentRemoteReceive(
                id = intentId ?: PublicKey.generate(),
                metadata = TransactionMetadata.ReceivePublicPayment(
                    source = giftCard.cluster.vaultPublicKey,
                    amount = amount,
                    mint = mint,
                    isIndirect = true,
                ),
                actionGroup = ActionGroup().apply {
                    actions = listOf(withdrawFromGiftCard)
                }
            )
        }
    }
}