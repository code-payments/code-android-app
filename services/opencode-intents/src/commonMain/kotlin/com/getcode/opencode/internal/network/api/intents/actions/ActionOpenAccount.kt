package com.getcode.opencode.internal.network.api.intents.actions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.accounts.AccountType
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.Mint

@InternalOpenCodeApi
class ActionOpenAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,
    val accountType: AccountType,
    val owner: AccountCluster,
    val mint: Mint,
    val authority: AccountCluster,
    val token: AccountCluster,
    val index: Long = 0
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()

    companion object {
        fun createPrimary(owner: AccountCluster, mint: Mint): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                authority = owner,
                token = owner,
                accountType = AccountType.Primary,
                mint = mint,
            )
        }

        fun createGiftCard(owner: AccountCluster, mint: Mint): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                authority = owner,
                token = owner,
                accountType = AccountType.RemoteSend,
                mint = mint,
            )
        }
    }
}