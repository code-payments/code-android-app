package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionPublicTransfer
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Distribution
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.solana.intents.buildActionGroup
import com.getcode.opencode.utils.generate
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
         * @param owner The account owner.
         * @param source The public key of the source of the distribution.
         * @param distributions The list of distributions to be made.
         */
        fun create(
            owner: AccountCluster,
            source: PublicKey,
            distributions: List<Distribution>
        ): IntentDistribution {
            val distroActions = buildActionGroup {
                distributions.forEach { distribution ->
                    add(
                        ActionPublicTransfer.newInstance(
                            owner = owner.authority.keyPair,
                            source = source,
                            amount = distribution.amount,
                            destination = distribution.destination,
                        )
                    )
                }
            }

            return IntentDistribution(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.PublicDistribution(
                    source = source,
                    distributions = distributions,
                ),
                actionGroup = distroActions,
            )
        }
    }
}