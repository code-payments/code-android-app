package com.getcode.opencode.internal.network.api.intents

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.TransactionService.OpenAccountsMetadata.AccountSet
import com.getcode.opencode.internal.network.api.intents.actions.ActionOpenAccount
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.solana.intents.ActionGroup
import com.getcode.opencode.solana.intents.IntentType
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey

internal class IntentCreateAccount(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
    val owner: AccountCluster,
    val accountType: AccountType,
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setOpenAccounts(
                TransactionService.OpenAccountsMetadata.newBuilder()
                    .setAccountSet(
                        when (accountType) {
                            AccountType.Pool -> AccountSet.USER
                            AccountType.Primary -> AccountSet.POOL
                            else -> AccountSet.UNRECOGNIZED
                        }
                    )
            )
            .build()
    }

    companion object {
        fun createUserAccount(owner: AccountCluster): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                owner = owner,
                accountType = AccountType.Primary,
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.createPrimary(owner))
                }
            )
        }

        fun createPoolAccount(owner: AccountCluster, nextIndex: Long): IntentCreateAccount {
            return IntentCreateAccount(
                id = PublicKey.generate(),
                owner = owner,
                accountType = AccountType.Pool,
                actionGroup = ActionGroup().apply {
                    actions = listOf(ActionOpenAccount.createPool(owner, nextIndex))
                }
            )
        }
    }
}