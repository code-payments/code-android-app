package com.getcode.model.intents.actions

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.model.Kin
import com.getcode.model.intents.ServerParameter
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.builder.TransactionBuilder
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.keys.TimelockDerivedAccounts
import com.getcode.solana.organizer.AccountCluster

class ActionCloseEmptyAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair? = null,

    val type: AccountType,
    val cluster: AccountCluster,

) : ActionType() {
    override fun transactions(): List<SolanaTransaction> {
        val timelock = cluster.timelock ?: return emptyList()
        return serverParameter?.configs?.map { config ->
            TransactionBuilder.closeEmptyAccount(
                timelockDerivedAccounts = timelock,
                maxDustAmount = Kin.fromKin(1),
                nonce = config.nonce,
                recentBlockhash = config.blockhash,
            )
        } ?: listOf()
    }

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply {
                this.id =
                    this@ActionCloseEmptyAccount.id
                this.closeEmptyAccount = TransactionService.CloseEmptyAccountAction.newBuilder().apply {
                    this.accountType = type.getAccountType()
                    this.authority =
                        this@ActionCloseEmptyAccount.cluster.authority.keyPair.publicKeyBytes.toSolanaAccount()
                    this.token =
                        this@ActionCloseEmptyAccount.cluster.vaultPublicKey.bytes.toSolanaAccount()
                }.build()
            }
            .build()
    }

    companion object {
        fun newInstance(
            type: AccountType,
            cluster: AccountCluster,
        ): ActionCloseEmptyAccount {
            return ActionCloseEmptyAccount(
                id = 0,
                type = type,
                cluster = cluster,
                signer = cluster.authority.keyPair,
            )
        }
    }
}