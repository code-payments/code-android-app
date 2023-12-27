package com.getcode.model.intents

import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Domain
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.network.repository.toPublicKey
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray

class IntentEstablishRelationship(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
    val organizer: Organizer,
    val domain: Domain,
    val resultTray: Tray
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()

            .setOpenAccounts(TransactionService.OpenAccountsMetadata.getDefaultInstance())
            .build()
    }

    companion object {
        fun newInstance(context: Context, organizer: Organizer, domain: Domain): IntentEstablishRelationship {
            val id = PublicKey.generate()
            val currentTray = organizer.tray.copy()

            val relationship = currentTray.createRelationship(context, domain)

            val ownerKey = currentTray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey()
            val actionOpenAccount = ActionOpenAccount.newInstance(
                owner = ownerKey,
                type = AccountType.Relationship(domain),
                accountCluster = relationship.getCluster()
            )

            return IntentEstablishRelationship(
                id = id,
                organizer = organizer,
                domain = domain,
                actionGroup = ActionGroup().apply {
                    actions = listOf(actionOpenAccount)
                },
                resultTray = currentTray
            )
        }
    }
}