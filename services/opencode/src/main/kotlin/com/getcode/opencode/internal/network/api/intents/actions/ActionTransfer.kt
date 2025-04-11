package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.model.account.AccountCluster
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.internal.network.api.intents.actions.ActionTransfer.Kind.*
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.solana.intents.actions.ActionType
import com.getcode.solana.keys.PublicKey

internal class ActionTransfer(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val kind: Kind,
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
            .apply {
                this.id =
                    this@ActionTransfer.id

                when (kind) {
                    Transfer -> {
                        this.noPrivacyTransfer =
                            TransactionService.NoPrivacyTransferAction.newBuilder()
                                .setSource(source.vaultPublicKey.asSolanaAccountId())
                                .setDestination(destination.asSolanaAccountId())
                                .setAuthority(source.authority.keyPair.asSolanaAccountId())
                                .setAmount(amount.quarks.toLong())
                                .build()
                    }

                    Withdraw -> {
                        this.noPrivacyWithdraw = TransactionService.NoPrivacyWithdrawAction.newBuilder()
                            .setSource(source.vaultPublicKey.asSolanaAccountId())
                            .setDestination(destination.asSolanaAccountId())
                            .setAuthority(source.authority.keyPair.asSolanaAccountId())
                            .setAmount(amount.quarks.toLong())
                            .setShouldClose(false)
                            .build()
                    }
                }
            }.build()
    }

    companion object {
        const val configCountRequirement: Int = 1

        fun newInstance(
            kind: Kind,
            amount: Fiat,
            sourceCluster: AccountCluster,
            destination: PublicKey
        ): ActionTransfer {
            return ActionTransfer(
                id = 0,
                signer = sourceCluster.authority.keyPair,
                kind = kind,
                amount = amount,
                source = sourceCluster,
                destination = destination
            )
        }
    }

    enum class Kind {
        Transfer,
        Withdraw
    }
}