package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.generate
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.solana.keys.PublicKey

internal class IntentCreateAccount(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
    val owner: AccountCluster,
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setOpenAccounts(TransactionService.OpenAccountsMetadata.getDefaultInstance())
            .build()
    }

    companion object {
        fun create(owner: AccountCluster): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                owner = owner,
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.create(owner))
                }
            )
        }
    }
}