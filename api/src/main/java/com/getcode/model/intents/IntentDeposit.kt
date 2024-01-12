package com.getcode.model.intents

import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.Kin
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray

class IntentDeposit(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val amount: Kin,
    private val source: AccountType,
    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setReceivePaymentsPrivately(
                TransactionService.ReceivePaymentsPrivatelyMetadata.newBuilder()
                    .setSource(organizer.tray.cluster(source).timelockAccounts.vault.publicKey.bytes.toSolanaAccount())
                    .setQuarks(amount.quarks)
                    .setIsDeposit(true)
            )
            .build()
    }

    companion object {
        fun newInstance(
            source: AccountType,
            organizer: Organizer,
            amount: Kin
        ): IntentDeposit {
            val intentId = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.slotsBalance

            // 1. Move all funds from the incoming
            // account to appropriate slots

            val transfers = currentTray.receive(receivingAccount = source, amount = amount).map { transfer ->
                ActionTransfer.newInstance(
                    kind = ActionTransfer.Kind.TempPrivacyTransfer,
                    intentId = intentId,
                    amount = transfer.kin,
                    source = currentTray.cluster(transfer.from),
                    destination =
                    currentTray.cluster(transfer.to!!).timelockAccounts.vault.publicKey
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
                    currentTray.cluster(exchange.to!!).timelockAccounts.vault.publicKey
                    // Exchanges always provide destination accounts
                )
            }

            val endBalance = currentTray.slotsBalance

            // We're just moving funds from incoming
            // account to buckets, the balance
            // shouldn't change
            if (endBalance != startBalance) {
                throw IntentReceive.Companion.IntentReceiveException.BalanceMismatchException()
            }

            val group = ActionGroup().apply {
                actions = listOf(
                    *transfers.toTypedArray(),
                    *redistributes.toTypedArray()
                )
            }

            return IntentDeposit(
                id = intentId,
                source = source,
                organizer = organizer,
                amount = amount,
                actionGroup = group,
                resultTray = currentTray
            )
        }
    }
}