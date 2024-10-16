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
    val kind: Kind,
    val cluster: AccountCluster,
    val amount: Kin,
    val configCountRequirement: Int = 1,
): ActionType() {

    sealed interface Kind {
        val codeType: Int
        data object Code: Kind {
            override val codeType: Int = 0
        }
        data class ThirdParty(val destination: com.getcode.solana.keys.PublicKey): Kind {
            override val codeType: Int = 1
        }
    }

    override fun transactions(): List<SolanaTransaction> {
        val configs = serverParameter?.configs ?: return emptyList()

        val timelock = cluster.timelock ?: return emptyList()

        val destination: com.getcode.solana.keys.PublicKey = when (kind) {
            Kind.Code -> {
                (serverParameter?.parameter as? ServerParameter.Parameter.FeePayment)?.publicKey ?: return emptyList()
            }
            is Kind.ThirdParty -> kind.destination
        }

        return configs.map { config ->
            TransactionBuilder.transfer(
                timelockDerivedAccounts = timelock,
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
                    .setTypeValue(kind.codeType)
                    .setAuthority(cluster.authority.keyPair.publicKeyBytes.toSolanaAccount())
                    .setSource(cluster.vaultPublicKey.bytes.toSolanaAccount())
                    .setAmount(amount.quarks)
                    .apply {
                        if (kind is Kind.ThirdParty) {
                            setDestination(kind.destination.bytes.toSolanaAccount())
                        }
                    }
                    .build()
            ).build()
    }

    companion object {
        fun newInstance(kind: Kind, cluster: AccountCluster, amount: Kin): ActionFeePayment {
            return ActionFeePayment(
                id = 0,
                signer = cluster.authority.keyPair,
                cluster = cluster,
                kind = kind,
                amount = amount
            )
        }
    }
}