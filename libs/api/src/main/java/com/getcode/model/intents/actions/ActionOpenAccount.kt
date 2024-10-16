package com.getcode.model.intents.actions

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.model.intents.ServerParameter
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.SolanaTransaction
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.utils.sign

class ActionOpenAccount(
    override var id: Int,
    override var serverParameter: ServerParameter? = null,
    override val signer: Ed25519.KeyPair?,
    val owner: com.getcode.solana.keys.PublicKey,
    val type: AccountType,
    val accountCluster: AccountCluster
) : ActionType() {
    //static let configCountRequirement: Int = 0

    override fun transactions(): List<SolanaTransaction> = listOf()

    override fun action(): TransactionService.Action {
        return TransactionService.Action.newBuilder()
            .apply {
                this.id =
                    this@ActionOpenAccount.id
                this.openAccount = TransactionService.OpenAccountAction.newBuilder().apply {
                    this.index =
                        this@ActionOpenAccount.accountCluster.index.toLong()
                    this.owner =
                        this@ActionOpenAccount.owner.bytes.toSolanaAccount()
                    this.accountType =
                        this@ActionOpenAccount.type.getAccountType()
                    this.authority =
                        this@ActionOpenAccount.accountCluster.authority.keyPair.publicKeyBytes.toSolanaAccount()
                    this.token =
                        this@ActionOpenAccount.accountCluster.vaultPublicKey
                            .bytes.toSolanaAccount()
                    this.authoritySignature =
                        this.sign(accountCluster.authority.keyPair)
                }.build()
            }
            .build()
    }

    companion object {
        fun newInstance(
            owner: com.getcode.solana.keys.PublicKey,
            type: AccountType,
            accountCluster: AccountCluster
        ): ActionOpenAccount {
            return ActionOpenAccount(
                id = 0,
                owner = owner,
                type = type,
                accountCluster = accountCluster,
                signer = null
            )
        }
    }
}