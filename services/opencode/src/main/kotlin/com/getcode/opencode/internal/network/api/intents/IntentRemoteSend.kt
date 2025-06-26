package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.internal.network.extensions.asExchangeData
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentRemoteSend(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
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

            // 2. Transfer all funds from primary account to the created gift card
            val transferToGiftCardAccount = ActionPublicTransfer.newInstance(
                amount = amount.usdc,
                owner = sourceCluster.authority.keyPair,
                source = sourceCluster.vaultPublicKey,
                destination = openGiftCardAccount.owner.vaultPublicKey
            )

            // 3. Allow auto-returning back to the primary if not collected
            val withdrawToDestination = ActionPublicWithdraw.newInstance(
                amount = amount.usdc,
                sourceCluster = giftCard.cluster,
                destination = sourceCluster.vaultPublicKey,
                canAutoReturn = true
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
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = giftCard.cluster.vaultPublicKey,
                    amount = amount,
                    isRemoteSend = true,
                    isWithdrawal = false
                ),
                actionGroup = actions
            )
        }
    }
}