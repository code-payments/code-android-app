package com.getcode.opencode.internal.network.api.intents.actions

import com.codeinc.opencode.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.network.extensions.asSolanaAccountId
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.Fee
import com.getcode.opencode.model.financial.FeeType
import com.getcode.opencode.solana.intents.CompactMessageArgs
import com.getcode.opencode.solana.intents.ServerParameter
import com.getcode.opencode.solana.intents.actions.ActionType

internal class ActionFeePayment(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val fee: Fee,
    val source: AccountCluster,
): ActionType() {
    override fun compactMessageArgs(): List<CompactMessageArgs> {
        val configs = serverParameter?.configs ?: return emptyList()

        val destination = when (fee.type) {
            FeeType.WithdrawalCreateOnSend -> {
                (serverParameter?.parameter as? ServerParameter.Parameter.FeePayment)?.publicKey
                    ?: return emptyList()
            }
        }

        return configs.map {
            val amountInQuarks = fee.fiat.quarks.toLong()
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
            .setFeePayment(
                TransactionService.FeePaymentAction.newBuilder()
                    .setSource(source.vaultPublicKey.asSolanaAccountId())
                    .setType(
                        when (fee.type) {
                            FeeType.WithdrawalCreateOnSend -> TransactionService.FeePaymentAction.FeeType.WITHDRAWAL_CREATE_ON_SEND
                        }
                    )
                    .setAuthority(source.authority.keyPair.asSolanaAccountId())
                    .setAmount(fee.fiat.quarks.toLong())
                    .build()
            ).build()
    }

    internal companion object {
        fun newInstance(
            fee: Fee,
            source: AccountCluster,
        ): ActionFeePayment {
            return ActionFeePayment(
                id = 0,
                signer = source.authority.keyPair,
                fee = fee,
                source = source,
            )
        }
    }
}