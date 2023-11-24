package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.model.intents.ServerParameter
import com.getcode.model.intents.actions.ActionTransfer.Kind.*
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.SplitterCommitmentAccounts
import com.getcode.solana.keys.SplitterTranscript

class ActionTransfer(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val kind: Kind,
    val intentId: PublicKey,
    val amount: Kin,
    val source: AccountCluster,
    val destination: PublicKey,
) : ActionType() {

    override fun transactions(): List<SolanaTransaction> {
        val serverParameter = serverParameter ?: return emptyList()
        val tempPrivacyParameter = serverParameter.parameter

        val resolvedDestination: PublicKey

        if (tempPrivacyParameter is ServerParameter.Parameter.TempPrivacy) {
            val splitterAccounts = SplitterCommitmentAccounts.newInstance(
                source = source,
                destination = destination,
                amount = amount,
                treasury = tempPrivacyParameter.treasury,
                recentRoot = tempPrivacyParameter.recentRoot,
                intentId = intentId,
                actionId = id
            )

            resolvedDestination = splitterAccounts.vault.publicKey
        } else {
            resolvedDestination = destination
        }

        return serverParameter.configs.map { config ->
            TransactionBuilder.transfer(
            timelockDerivedAccounts = source.timelockAccounts,
            destination = resolvedDestination,
            amount = amount,
            nonce = config.nonce,
            recentBlockhash = config.blockhash,
            kreIndex = kreIndex
            )
        }
    }

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply {
                this.id =
                    this@ActionTransfer.id

                when (kind) {
                    TempPrivacyTransfer -> {
                        this.temporaryPrivacyTransfer =
                            TransactionService.TemporaryPrivacyTransferAction.newBuilder().apply {
                                this.source =
                                    this@ActionTransfer.source.timelockAccounts.vault.publicKey.bytes.toSolanaAccount()
                                this.destination =
                                    this@ActionTransfer.destination.bytes.toSolanaAccount()
                                this.authority =
                                    this@ActionTransfer.source.authority.keyPair.publicKeyBytes.toSolanaAccount()
                                this.amount =
                                    this@ActionTransfer.amount.quarks
                            }.build()
                    }
                    TempPrivacyExchange -> {
                        this.temporaryPrivacyExchange =
                            TransactionService.TemporaryPrivacyExchangeAction.newBuilder().apply {
                                this.source =
                                    this@ActionTransfer.source.timelockAccounts.vault.publicKey.bytes.toSolanaAccount()
                                this.destination =
                                    this@ActionTransfer.destination.bytes.toSolanaAccount()
                                this.authority =
                                    this@ActionTransfer.source.authority.keyPair.publicKeyBytes.toSolanaAccount()
                                this.amount =
                                    this@ActionTransfer.amount.quarks
                            }.build()
                    }
                    NoPrivacyTransfer -> {
                        this.noPrivacyTransfer =
                            TransactionService.NoPrivacyTransferAction.newBuilder().apply {
                                this.source =
                                    this@ActionTransfer.source.timelockAccounts.vault.publicKey.bytes.toSolanaAccount()
                                this.destination =
                                    this@ActionTransfer.destination.bytes.toSolanaAccount()
                                this.authority =
                                    this@ActionTransfer.source.authority.keyPair.publicKeyBytes.toSolanaAccount()
                                this.amount =
                                    this@ActionTransfer.amount.quarks
                            }.build()
                    }
                }
            }.build()


    }

    companion object {
        fun newInstance(
            kind: Kind,
            intentId: PublicKey,
            amount: Kin,
            source: AccountCluster,
            destination: PublicKey
        ): ActionTransfer {
            return ActionTransfer(
                id = 0,
                signer = source.authority.keyPair,
                kind = kind,
                intentId = intentId,
                amount = amount,
                source = source,
                destination = destination
            )
        }

        const val configCountRequirement: Int = 1
    }

    enum class Kind {
        TempPrivacyTransfer,
        TempPrivacyExchange,
        NoPrivacyTransfer,
    }
}