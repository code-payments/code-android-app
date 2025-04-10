package com.getcode.opencode.internal.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.codeinc.opencode.gen.transaction.v2.openAccountAction
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.intents.ServerParameter
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.solana.SolanaTransaction

internal class ActionOpenAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val owner: AccountCluster,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .setId(id)
            .setOpenAccount(openAccountAction {
                index = 0
                owner = this@ActionOpenAccount.owner.authorityPublicKey.asSolanaAccountId()
                accountType
            }).build()
    }

    companion object {
        fun create(owner: AccountCluster): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner
            )
        }
    }
}