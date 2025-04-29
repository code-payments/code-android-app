package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.PublicKey

internal class ActionPublicTransfer(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val amount: Fiat,
    val source: AccountCluster,
    val destination: PublicKey,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> = listOf()
    override fun compactMessageArgs(): List<CompactMessageArgs> {
        val configs = serverParameter?.configs ?: return emptyList()
        return configs.map {

            val amountInQuarks = amount.quarks.toLong()
            val nonceAccount = it.nonce
            val nonceValue = it.blockhash

            CompactMessageArgs.Transfer(
                source = source.vaultPublicKey,
                destination = destination,
                amountInQuarks = amountInQuarks,
                nonce = nonceAccount,
                nonceValue = nonceValue
            )
        }
    }


    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .setId(id)
            .setNoPrivacyTransfer(
                TransactionService.NoPrivacyTransferAction.newBuilder()
                    .setSource(source.vaultPublicKey.asSolanaAccountId())
                    .setDestination(destination.asSolanaAccountId())
                    .setAuthority(source.authority.keyPair.asSolanaAccountId())
                    .setAmount(amount.quarks.toLong())
                    .build()
            ).build()
    }

    internal companion object {
        fun newInstance(
            amount: Fiat,
            sourceCluster: AccountCluster,
            destination: PublicKey
        ): ActionPublicTransfer {
            return ActionPublicTransfer(
                id = 0,
                signer = sourceCluster.authority.keyPair,
                amount = amount,
                source = sourceCluster,
                destination = destination
            )
        }
    }
}