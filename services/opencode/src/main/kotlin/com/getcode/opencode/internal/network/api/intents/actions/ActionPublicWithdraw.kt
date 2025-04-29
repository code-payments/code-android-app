package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.PublicKey

class ActionPublicWithdraw(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair?,
    val source: AccountCluster,
    val destination: PublicKey,
    val amount: Fiat,
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

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .setId(id)
            .setNoPrivacyWithdraw(
                TransactionService.NoPrivacyWithdrawAction.newBuilder()
                    .setSource(source.vaultPublicKey.asSolanaAccountId())
                    .setDestination(destination.asSolanaAccountId())
                    .setAuthority(source.authority.keyPair.asSolanaAccountId())
                    .setAmount(amount.quarks.toLong())
                    .setShouldClose(true)
                    .setIsAutoReturn(canAutoReturn)
                    .build()
            )
            .build()
    }

    internal companion object {
        fun newInstance(
            amount: Fiat,
            sourceCluster: AccountCluster,
            destination: PublicKey,
            canAutoReturn: Boolean,
        ): ActionPublicWithdraw {
            return ActionPublicWithdraw(
                id = 0,
                signer = sourceCluster.authority.keyPair,
                amount = amount,
                source = sourceCluster,
                destination = destination,
                canAutoReturn = canAutoReturn
            )
        }
    }
}