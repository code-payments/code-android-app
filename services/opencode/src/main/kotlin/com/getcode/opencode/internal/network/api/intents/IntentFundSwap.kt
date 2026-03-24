package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.internal.solana.extensions.timelockSwapAccounts
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.solana.keys.PublicKey

internal class IntentFundSwap(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    companion object {
        fun create(
            intentId: PublicKey,
            sourceCluster: AccountCluster,
            amount: LocalFiat,
            fromMint: Token,
            verifiedState: VerifiedState,
        ): IntentFundSwap {
            val timelockAccounts = fromMint.timelockSwapAccounts(sourceCluster.authorityPublicKey)

            val destination = timelockAccounts.ata.publicKey
            val destinationOwner = timelockAccounts.pda.publicKey

            val transfer = ActionPublicTransfer.newInstance(
                amount = amount.underlyingTokenAmount,
                source = sourceCluster.vaultPublicKey,
                owner = sourceCluster.authority.keyPair,
                destination = destination,
                mint = fromMint.address,
            )

            return IntentFundSwap(
                id = intentId,
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    destinationOwner = destinationOwner,
                    amount = amount,
                    verifiedState = verifiedState,
                    mint = fromMint.address,
                    isRemoteSend = false,
                    isWithdrawal = true,
                ),
                actionGroup = buildActionGroup { add(transfer) },
            )
        }
    }
}