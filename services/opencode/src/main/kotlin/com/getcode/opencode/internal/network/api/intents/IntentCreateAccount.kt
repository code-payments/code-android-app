package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.network.extensions.asProtobufMetadata
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.PoolAccount
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

internal class IntentCreateAccount(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return metadata.asProtobufMetadata()
    }

    companion object {
        fun createUserAccount(owner: AccountCluster): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.OpenAccount(AccountType.Primary),
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.createPrimary(owner))
                }
            )
        }

        fun createPoolAccount(owner: AccountCluster, pool: AccountCluster, index: Long): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.OpenAccount(AccountType.Pool),
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.createPool(owner, pool, index))
                }
            )
        }
    }
}