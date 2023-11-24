package com.getcode.model.intents

import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.model.Kin
import com.getcode.model.intents.actions.ActionCloseEmptyAccount
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray

class IntentMigratePrivacy(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val amount: Kin,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setMigrateToPrivacy2022(
                TransactionService.MigrateToPrivacy2022Metadata.newBuilder()
                    .setQuarks(amount.quarks)
            )
            .build()
    }

    companion object {
        fun newInstance(context: Context, organizer: Organizer, amount: Kin): IntentMigratePrivacy {
            val intentId = PublicKey.generate()

            val legacyCluster = AccountCluster.newInstance(
                authority = DerivedKey.derive(
                    context = context,
                    path = DerivePath.primary,
                    mnemonic = organizer.mnemonic
                ),
                legacy = true
            )

            val tray = organizer.tray
            val group = ActionGroup()

            if (amount.quarks > 0) {
                // If there's a balance in the legacy account
                // we'll move the funds over to a new private
                // primary account
                group.actions = listOf(
                    ActionWithdraw.newInstance(
                        kind = ActionWithdraw.Kind.NoPrivacyWithdraw(amount),
                        cluster = legacyCluster,
                        destination = organizer.primaryVault,
                        legacy = true
                    )
                )
                tray.increment(type = AccountType.Primary, kin = amount)
            } else {
                // If there's no balance, we can
                // simply close the account
                group.actions = listOf(
                    ActionCloseEmptyAccount.newInstance(
                        type = AccountType.Primary,
                        cluster = legacyCluster,
                        legacy = true
                    )
                )
            }

            return IntentMigratePrivacy(
                id = intentId,
                organizer = organizer,
                amount = amount,
                actionGroup = group,
                resultTray = tray
            )
        }
    }

}