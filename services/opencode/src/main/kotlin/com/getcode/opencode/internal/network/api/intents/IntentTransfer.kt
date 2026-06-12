package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.OcpTransactionService
import com.getcode.opencode.internal.manager.VerifiedState
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.transactions.ExchangeData
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

/**
 * A SendPublicPayment intent for transferring funds to another account.
 *
 * Two creation paths are supported:
 * - **Rendezvous-based** ([create] with [ExchangeData.Verified]): used for
 *   peer-to-peer bill gives where the rendezvous key serves as the intent id.
 * - **Direct** ([create] with [VerifiedState]): used for direct sends to a
 *   known recipient (e.g. Flipcash contact). A random intent id is generated
 *   and the [destinationOwner] is included in the metadata.
 */
internal class IntentTransfer(
    override val id: PublicKey,
    override val  metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): OcpTransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    companion object {
        /** Creates a rendezvous-based transfer for peer-to-peer bill gives. */
        fun create(
            amount: LocalFiat,
            mint: Mint,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            rendezvous: PublicKey,
            exchangeData: ExchangeData.Verified,
        ): IntentTransfer {
            val transfer = ActionPublicTransfer.newInstance(
                owner = sourceCluster.authority.keyPair,
                source = sourceCluster.vaultPublicKey,
                destination = destination,
                amount = amount.underlyingTokenAmount,
                mint = mint,
            )

            return IntentTransfer(
                id = rendezvous,
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    amount = amount,
                    mint = mint,
                    isIndirect = false,
                    isWithdrawal = false,
                    exchangeData = exchangeData,
                ),
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                }
            )
        }

        /** Creates a direct transfer to a known recipient's vault. */
        fun create(
            amount: LocalFiat,
            mint: Mint,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            destinationOwner: PublicKey,
            verifiedState: VerifiedState,
        ): IntentTransfer {
            val transfer = ActionPublicTransfer.newInstance(
                owner = sourceCluster.authority.keyPair,
                source = sourceCluster.vaultPublicKey,
                destination = destination,
                amount = amount.underlyingTokenAmount,
                mint = mint,
            )

            return IntentTransfer(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.SendPublicPayment(
                    source = sourceCluster.vaultPublicKey,
                    destination = destination,
                    destinationOwner = destinationOwner,
                    amount = amount,
                    verifiedState = verifiedState,
                    mint = mint,
                    isIndirect = false,
                    isWithdrawal = false,
                ),
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                },
            )
        }
    }
}