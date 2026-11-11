package com.getcode.opencode.internal.network.api.intents.actions

import com.getcode.opencode.annotations.InternalOpenCodeApi
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlin.math.min

@InternalOpenCodeApi
class ActionPublicWithdraw(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair,
    val source: AccountCluster,
    val destination: PublicKey,
    val amount: Fiat,
    val mint: Mint,
    val canAutoReturn: Boolean,
): ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun compactMessageArgs(): List<CompactMessageArgs> {
        val configs = serverParameter?.configs ?: return emptyList()
        return configs.map {
            val nonceAccount = it.nonce
            val nonceValue = it.blockhash

            CompactMessageArgs.Withdraw(
                source = source.vaultPublicKey,
                destination = destination,
                nonce = nonceAccount,
                nonceValue = nonceValue
            )
        }
    }


    companion object {
        fun newInstance(
            amount: Fiat,
            mint: Mint,
            owner: AccountCluster,
            source: AccountCluster,
            destination: PublicKey,
            canAutoReturn: Boolean,
        ): ActionPublicWithdraw {
            return ActionPublicWithdraw(
                id = 0,
                signer = owner.authority.keyPair,
                amount = amount,
                mint = mint,
                source = source,
                destination = destination,
                canAutoReturn = canAutoReturn
            )
        }
    }
}