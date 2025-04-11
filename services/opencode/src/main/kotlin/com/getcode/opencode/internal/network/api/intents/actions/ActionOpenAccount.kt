package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.actions.ActionType

internal class ActionOpenAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val owner: AccountCluster,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply trx@{
                val source = this@ActionOpenAccount.owner
                this.id = id
                this.setOpenAccount(TransactionService.OpenAccountAction.newBuilder()
                    .setIndex(0)
                    .setOwner(source.authorityPublicKey.asSolanaAccountId())
                    .setAccountType(Model.AccountType.PRIMARY)
                    .setAuthority(source.authorityPublicKey.asSolanaAccountId())
                    .setToken(source.vaultPublicKey.asSolanaAccountId())
                    .apply {
                        setAuthoritySignature(sign(source.authority.keyPair))
                    }
                )
            }
            .build()
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