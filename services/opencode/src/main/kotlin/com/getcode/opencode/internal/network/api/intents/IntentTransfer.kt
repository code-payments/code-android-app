package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.asExchangeData
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentTransfer(
    override val id: PublicKey,
    override val  metadata: TransactionMetadata,
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
            rendezvous: PublicKey,
        ): IntentTransfer {
            val transfer = ActionPublicTransfer.newInstance(
                owner = sourceCluster.authority.keyPair,
                source = sourceCluster.vaultPublicKey,
                destination = destination,
                amount = amount.usdc
            )

            return IntentTransfer(
                id = rendezvous,
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    amount = amount,
                    isRemoteSend = false,
                    isWithdrawal = false,
                ),
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                }
            )
        }
    }
}