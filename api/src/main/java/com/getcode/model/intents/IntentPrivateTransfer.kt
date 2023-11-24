package com.getcode.model.intents

import android.content.Context
import android.util.Log
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.KinAmount
import com.getcode.model.intents.actions.ActionOpenAccount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toPublicKey
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.*
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import timber.log.Timber

class IntentPrivateTransfer(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val destination: PublicKey,
    private val amount: KinAmount,
    private val isWithdrawal: Boolean,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPrivatePayment(
                TransactionService.SendPrivatePaymentMetadata.newBuilder()
                    .setDestination(destination.bytes.toSolanaAccount())
                    .setIsWithdrawal(isWithdrawal)
                    .setExchangeData(
                        TransactionService.ExchangeData.newBuilder()
                            .setQuarks(amount.kin.quarks)
                            .setCurrency(amount.rate.currency.name.lowercase())
                            .setExchangeRate(amount.rate.fx)
                            .setNativeAmount(amount.fiat)
                        )
            )
            .build()
    }

    companion object {
        fun newInstance(
            context: Context,
            rendezvousKey: PublicKey,
            organizer: Organizer,
            destination: PublicKey,
            amount: KinAmount,
            isWithdrawal: Boolean
        ): IntentPrivateTransfer {
            val currentTray = organizer.tray.copy()
            val startBalance = currentTray.availableBalance

            // 1. Move all funds from bucket accounts into the
            // outgoing account and prepare to transfer

            val transfers = currentTray.transfer(amount = amount.kin).map { transfer ->
                val sourceCluster = currentTray.cluster(transfer.from)

                // If the transfer is to another bucket, it's an internal
                // exchange. Otherwise, it is considered a transfer.
                if (transfer.to is AccountType.Bucket) {
                    ActionTransfer.newInstance(
                        kind = ActionTransfer.Kind.TempPrivacyExchange,
                        intentId = rendezvousKey,
                        amount = transfer.kin,
                        source = sourceCluster,
                        destination = currentTray.slot((transfer.to as AccountType.Bucket).type).getCluster().timelockAccounts.vault.publicKey
                    )
                } else {
                    ActionTransfer.newInstance(
                        kind = ActionTransfer.Kind.TempPrivacyTransfer,
                        intentId = rendezvousKey,
                        amount = transfer.kin,
                        source = sourceCluster,
                        destination = currentTray.outgoing.getCluster().timelockAccounts.vault.publicKey
                    )
                }
            }

            // 2. Transfer all collected funds from the temp
            // outgoing account to the destination account

            val outgoing = ActionWithdraw.newInstance(
                kind = ActionWithdraw.Kind.NoPrivacyWithdraw(amount.kin),
                cluster = currentTray.outgoing.getCluster(),
                destination = destination
            )

            // 3. Redistribute the funds to optimize for a
            // subsequent payment out of the buckets

            val redistributes = currentTray.redistribute().map { exchange ->
                ActionTransfer.newInstance(
                    kind = ActionTransfer.Kind.TempPrivacyExchange,
                    intentId = rendezvousKey,
                    amount = exchange.kin,
                    source = currentTray.cluster(exchange.from),
                    destination = currentTray.cluster(exchange.to!!).timelockAccounts.vault.publicKey
                    // Exchanges always provide destination accounts
                )
            }

            // 4. Rotate the outgoing account

            currentTray.incrementOutgoing(context)
            val newOutgoing = currentTray.outgoing

            val rotation = listOf(
                ActionOpenAccount.newInstance(
                    owner = organizer.tray.owner.getCluster().authority.keyPair.publicKeyBytes.toPublicKey(),
                    type = AccountType.Outgoing,
                    accountCluster = newOutgoing.getCluster()
                ),
                ActionWithdraw.newInstance(
                    kind = ActionWithdraw.Kind.CloseDormantAccount(AccountType.Outgoing),
                    cluster = newOutgoing.getCluster(),
                    destination = currentTray.owner.getCluster().timelockAccounts.vault.publicKey
                )
            )

            val endBalance = currentTray.availableBalance

            if (startBalance - endBalance != amount.kin)  {
                Timber.e(
                    "Expected: ${amount.kin}; actual = ${startBalance - endBalance}; " +
                            "difference: ${startBalance.quarks - currentTray.availableBalance.quarks - amount.kin.quarks}"
                )
                throw IntentPrivateTransferException.BalanceMismatchException()
            }

            val group = ActionGroup().apply {
                actions = listOf(
                    *transfers.toTypedArray(),
                    outgoing,
                    *redistributes.toTypedArray(),
                    *rotation.toTypedArray()
                )
            }

            return IntentPrivateTransfer(
                id = rendezvousKey,
                organizer = organizer,
                destination = destination,
                amount = amount,
                isWithdrawal = isWithdrawal,
                actionGroup = group,
                resultTray = currentTray,
            )

        }
    }
}

sealed class IntentPrivateTransferException: Exception() {
    class BalanceMismatchException: IntentPrivateTransferException()
}