package com.getcode.opencode.internal.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.opencode.internal.extensions.generate
import com.getcode.opencode.internal.intents.actions.ActionOpenAccount
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.solana.keys.PublicKey

class IntentCreateAccount(
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