package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.model.intents.PrivateTransferMetadata
import com.getcode.model.intents.ServerParameter
import com.getcode.model.toPublicKey
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType

class ActionWithdraw(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair?,

    val kind: Kind,

    val cluster: AccountCluster,
    val destination: com.getcode.solana.keys.PublicKey,
    val legacy: Boolean,
    val metadata: PrivateTransferMetadata? = null,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> {
        val timelock = cluster.timelock ?: return emptyList()
        return serverParameter?.configs?.map { config ->
            TransactionBuilder.closeDormantAccount(
                authority = cluster.authority.keyPair.publicKeyBytes.toPublicKey(),
                timelockDerivedAccounts =  timelock,
                destination =  destination,
                nonce =  config.nonce,
                recentBlockhash = config.blockhash,
                kreIndex =  kreIndex,
                legacy = legacy,
                metadata = metadata,
            )
        }.orEmpty()
    }

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply {
                this.id =
                    this@ActionWithdraw.id

                when (kind) {
                    is Kind.CloseDormantAccount -> {
                        this.closeDormantAccount =
                            TransactionService.CloseDormantAccountAction.newBuilder().apply {
                            this.accountType =
                                this@ActionWithdraw.kind.accountType.getAccountType()
                            this.authority =
                                this@ActionWithdraw.cluster.authority.keyPair.publicKeyBytes.toSolanaAccount()
                            this.token =
                                this@ActionWithdraw.cluster.vaultPublicKey.bytes.toSolanaAccount()
                            this.destination =
                                this@ActionWithdraw.destination.bytes.toSolanaAccount()
                        }.build()
                    }
                    is Kind.NoPrivacyWithdraw -> {
                        this.noPrivacyWithdraw =
                            TransactionService.NoPrivacyWithdrawAction.newBuilder().apply {
                                this.authority =
                                    this@ActionWithdraw.cluster.authority.keyPair.publicKeyBytes.toSolanaAccount()
                                this.source =
                                    this@ActionWithdraw.cluster.vaultPublicKey.bytes.toSolanaAccount()
                                this.destination =
                                    this@ActionWithdraw.destination.bytes.toSolanaAccount()
                                this.amount =
                                    this@ActionWithdraw.kind.amount.quarks
                                this.shouldClose =
                                    true
                            }.build()
                    }
                }
            }.build()
    }

    companion object {
        fun newInstance(
            kind: Kind,
            cluster: AccountCluster,
            destination: com.getcode.solana.keys.PublicKey,
            legacy: Boolean = false,
            metadata: PrivateTransferMetadata? = null,
        ): ActionWithdraw {
            return ActionWithdraw(
                id = 0,
                signer = cluster.authority.keyPair,

                kind = kind,
                cluster = cluster,
                destination = destination,
                legacy = legacy,
                metadata = metadata
            )
        }

        const val configCountRequirement: Int = 1
    }

    sealed class Kind {
        data class CloseDormantAccount(val accountType: AccountType) : Kind()
        data class NoPrivacyWithdraw(val amount: Kin): Kind()
    }
}