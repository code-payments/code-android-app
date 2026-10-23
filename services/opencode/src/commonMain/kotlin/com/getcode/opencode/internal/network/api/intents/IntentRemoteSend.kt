package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.GiftCardAccount
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentRemoteSend(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup
): IntentType() {
    override fun metadata(): OcpTransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    internal companion object {
        fun create(
            rendezvous: PublicKey,
            owner: AccountCluster,
            source: AccountCluster,
            giftCard: GiftCardAccount,
            amount: LocalFiat,
            token: Token,
            verifiedState: VerifiedState,
        ): IntentRemoteSend {
            // 1. Open gift card account
            val openGiftCardAccount = ActionOpenAccount.createGiftCard(giftCard.cluster, token.address)

            // 2. Transfer all funds from primary account to the created gift card
            val transferToGiftCardAccount = ActionPublicTransfer.newInstance(
                amount = amount.underlyingTokenAmount,
                owner = owner.authority.keyPair,
                source = source.vaultPublicKey,
                destination = openGiftCardAccount.owner.vaultPublicKey,
                mint = token.address
            )

            // 3. Allow auto-returning back to the primary if not collected
            val withdrawToDestination = ActionPublicWithdraw.newInstance(
                amount = amount.underlyingTokenAmount,
                owner = giftCard.cluster,
                source = giftCard.cluster,
                destination = source.vaultPublicKey,
                canAutoReturn = true,
                mint = token.address
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
                    source = source.vaultPublicKey,
                    destination = giftCard.cluster.vaultPublicKey,
                    amount = amount,
                    mint = token.address,
                    isIndirect = true,
                    isWithdrawal = false,
                    verifiedState = verifiedState,
                ),
                actionGroup = actions
            )
        }
    }
}