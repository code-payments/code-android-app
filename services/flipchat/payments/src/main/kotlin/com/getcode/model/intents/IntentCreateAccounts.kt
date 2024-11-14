package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.CodeTransactionService as TransactionService
import com.getcode.ed25519.Ed25519
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.intents.actions.ActionType
import com.getcode.model.toPublicKey
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.Organizer

class IntentCreateAccounts(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
    private val organizer: Organizer,
) : IntentType() {

    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setOpenAccounts(TransactionService.OpenAccountsMetadata.getDefaultInstance())
            .build()
    }


    companion object {
        fun newInstance(organizer: Organizer): IntentCreateAccounts {
            val actionsList = mutableListOf<ActionType>().apply {
                organizer.allAccounts().map { pair ->
                    val (type, cluster) = pair
                    ActionOpenAccount.newInstance(
                        owner = organizer.tray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(),
                        type = type,
                        accountCluster = cluster
                    ).let { this.add(it) }
                }
            }

            return IntentCreateAccounts(
                id = Ed25519.createKeyPair().publicKeyBytes.toPublicKey(),
                organizer = organizer,
                actionGroup = ActionGroup().apply {
                    actions = actionsList
                }
            )

        }
    }
}