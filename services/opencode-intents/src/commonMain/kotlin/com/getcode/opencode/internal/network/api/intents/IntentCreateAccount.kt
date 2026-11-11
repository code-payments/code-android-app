package com.getcode.opencode.internal.network.api.intents

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.model.accounts.PoolAccount
import com.getcode.opencode.model.transactions.TransactionMetadata
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey

@InternalOpenCodeApi
class IntentCreateAccount(
    override val id: PublicKey,
    override val metadata: TransactionMetadata,
    override val actionGroup: ActionGroup,
) : IntentType() {


    companion object {
        fun createUserAccount(owner: AccountCluster, mint: Mint): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                metadata = TransactionMetadata.OpenAccount(AccountType.Primary, mint),
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.createPrimary(owner, mint))
                }
            )
        }
    }
}