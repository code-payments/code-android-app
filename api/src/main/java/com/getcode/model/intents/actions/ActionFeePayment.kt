package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.codeinc.gen.transaction.v2.TransactionService.FeePaymentAction
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.model.intents.ServerParameter
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.organizer.AccountCluster

class ActionFeePayment(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val cluster: AccountCluster,
    val amount: Kin,
    val configCountRequirement: Int = 1,
): ActionType() {
    override fun transactions(): List<SolanaTransaction> {
        val configs = serverParameter?.configs ?: return emptyList()

        val destination = (serverParameter?.parameter as? ServerParameter.Parameter.FeePayment)?.publicKey ?: return emptyList()

        return configs.map { config ->
            TransactionBuilder.transfer(
                timelockDerivedAccounts = cluster.timelockAccounts,
                destination = destination,
                amount = amount,
                nonce = config.nonce,
                recentBlockhash = config.blockhash,
                kreIndex = kreIndex
            )
        }
    }

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .setId(id)
            .setFeePayment(
                FeePaymentAction.newBuilder()
                    .setAuthority(cluster.authority.keyPair.publicKeyBytes.toSolanaAccount())
                    .setSource(cluster.timelockAccounts.vault.publicKey.bytes.toSolanaAccount())
                    .setAmount(amount.quarks)
                    .build()
            ).build()
    }

    companion object {
        fun newInstance(cluster: AccountCluster, amount: Kin): ActionFeePayment {
            return ActionFeePayment(
                id = 0,
                signer = cluster.authority.keyPair,
                cluster = cluster,
                amount = amount
            )
        }
    }
}