package com.getcode.model.intents

import com.codeinc.gen.common.v1.Model
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Domain
import com.getcode.model.generate
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.toPublicKey
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Relationship
import com.getcode.solana.organizer.Tray

class IntentEstablishRelationship(
    override val id: PublicKey,
    override val actionGroup: ActionGroup,
    val organizer: Organizer,
    val domain: Domain,
    val resultTray: Tray,
    val relationship: Relationship,
): IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setEstablishRelationship(
                TransactionService.EstablishRelationshipMetadata.newBuilder()
                    .setRelationship(
                        Model.Relationship.newBuilder()
                            .setDomain(Model.Domain.newBuilder().setValue(domain.relationshipHost))
                    )
            )
            .build()
    }

    companion object {
        fun newInstance(organizer: Organizer, domain: Domain): IntentEstablishRelationship {
            val id = PublicKey.generate()
            val currentTray = organizer.tray.copy()

            val relationship = currentTray.createRelationship(domain)

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
                resultTray = currentTray,
                relationship = relationship,
            )
        }
    }
}