package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.internal.network.extensions.sign
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.actions.ActionType

internal class ActionOpenAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,
    private val accountType: AccountType,
    val owner: AccountCluster,
    private val authority: AccountCluster,
    private val token: AccountCluster,
    private val index: Long = 0
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply trx@{
                val index = this@ActionOpenAccount.index
                val owner = this@ActionOpenAccount.owner
                this.id = id
                this.setOpenAccount(TransactionService.OpenAccountAction.newBuilder()
                    .setIndex(index)
                    .setOwner(owner.authorityPublicKey.asSolanaAccountId())
                    .setAccountType(accountType.getAccountType())
                    .setAuthority(authority.authorityPublicKey.asSolanaAccountId())
                    .setToken(token.vaultPublicKey.asSolanaAccountId())
                    .apply {
                        setAuthoritySignature(sign(this@ActionOpenAccount.authority.authority.keyPair))
                    }
                )
            }
            .build()
    }

    companion object {
        fun createPrimary(owner: AccountCluster): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                authority = owner,
                token = owner,
                accountType = AccountType.Primary
            )
        }

        fun createGiftCard(owner: AccountCluster): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                authority = owner,
                token = owner,
                accountType = AccountType.RemoteSend
            )
        }

        fun createPool(owner: AccountCluster, pool: AccountCluster, index: Long): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                authority = pool,
                token = pool,
                accountType = AccountType.Pool,
                index = index
            )
        }
    }
}