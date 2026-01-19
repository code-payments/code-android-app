package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v1.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicWithdraw
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

internal class IntentDistribution(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    companion object {
        /**
         * Creates a new intent facilitating a public distribution.
         *
         * The distribution is a series of public transfers. The last distribution
         * is a public withdrawal of the remaining funds, which will in turn allow the account
         * to be closed.
         *
         * @param owner The account owner.
         * @param source The public key of the source of the distribution.
         * @param distributions The list of distributions to be made.
         */
        fun create(
            owner: AccountCluster,
            source: AccountCluster,
            distributions: List<Distribution>,
            mint: Mint,
        ): IntentDistribution {
            val distroActions = buildActionGroup {
                distributions.dropLast(1).forEach { dist ->
                    add(
                        ActionPublicTransfer.newInstance(
                            owner = source.authority.keyPair,
                            source = source.vaultPublicKey,
                            amount = dist.amount,
                            destination = dist.destination,
                            mint = mint,
                        )
                    )
                }
                distributions.lastOrNull()?.let { dist ->
                    add(
                        ActionPublicWithdraw.newInstance(
                            amount = dist.amount,
                            owner = source,
                            source = source,
                            destination = dist.destination,
                            canAutoReturn = false,
                            mint = mint
                        )
                    )
                }
            }

            return IntentDistribution(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.PublicDistribution(
                    source = source.vaultPublicKey,
                    distributions = distributions,
                    mint = mint
                ),
                actionGroup = distroActions,
            )
        }
    }
}