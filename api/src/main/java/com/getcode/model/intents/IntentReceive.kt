package com.getcode.model.intents

import android.content.Context
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Kin
import com.getcode.model.intents.actions.*
import com.getcode.network.repository.toPublicKey
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.Tray

class IntentReceive(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val amount: Kin,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setReceivePaymentsPrivately(
                TransactionService.ReceivePaymentsPrivatelyMetadata.newBuilder()
                    .setSource(organizer.tray.incoming.getCluster().vaultPublicKey.bytes.toSolanaAccount())
                    .setQuarks(amount.quarks)
                    .setIsDeposit(false)
            )
            .build()
    }

    companion object {
        fun newInstance(
            context: Context,
            organizer: Organizer,
            amount: Kin
        ): IntentReceive {
            val intentId = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.availableBalance

            // 1. Move all funds from the incoming
            // account to appropriate slots

            val transfers = currentTray.receive(AccountType.Incoming, amount = amount).map { transfer ->
                ActionTransfer.newInstance(
                    kind = ActionTransfer.Kind.TempPrivacyTransfer,
                    intentId = intentId,
                    amount = transfer.kin,
                    source = currentTray.cluster(transfer.from),
                    destination =
                    currentTray.cluster(transfer.to!!).vaultPublicKey
                )
            }

            // 2. Redistribute the funds to prepare for
            // future transfers

            val redistributes = currentTray.redistribute().map { exchange ->
                ActionTransfer.newInstance(
                    kind = ActionTransfer.Kind.TempPrivacyExchange,
                    intentId = intentId,
                    amount = exchange.kin,
                    source = currentTray.cluster(exchange.from),
                    destination =
                    currentTray.cluster(exchange.to!!).vaultPublicKey
                    // Exchanges always provide destination accounts
                )
            }

            // 3. Rotate incoming account

            val oldIncoming = currentTray.incoming
            currentTray.incrementIncoming(context)
            val newIncoming = currentTray.incoming

            val rotation = mutableListOf(
                ActionCloseEmptyAccount.newInstance(
                    type = AccountType.Incoming,
                    cluster = oldIncoming.getCluster()
                ),
                ActionOpenAccount.newInstance(
                    owner = organizer.tray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(),
                    type = AccountType.Incoming,
                    accountCluster = newIncoming.getCluster()
                ),
                ActionWithdraw.newInstance(
                    kind = ActionWithdraw.Kind.CloseDormantAccount(AccountType.Incoming),
                    cluster = newIncoming.getCluster(),
                    destination = organizer.tray.owner.getCluster().vaultPublicKey
                )
            )

            val endBalance = currentTray.availableBalance

            // We're just moving funds from incoming
            // account to buckets, the balance
            // shouldn't change
            if (endBalance != startBalance) {
                throw IntentReceiveException.BalanceMismatchException()
            }

            val group = ActionGroup().apply {
                actions = listOf(
                    *transfers.toTypedArray(),
                    *redistributes.toTypedArray(),
                    *rotation.toTypedArray()
                )
            }

            return IntentReceive(
                id = intentId,
                organizer = organizer,
                amount = amount,
                actionGroup = group,
                resultTray = currentTray,
            )
        }

        sealed class IntentReceiveException : Exception() {
            class BalanceMismatchException : IntentReceiveException()
        }
    }
}
