package com.getcode.model.intents

import android.util.Log
import com.codeinc.gen.transaction.v2.TransactionService
import com.getcode.model.KinAmount
import com.getcode.model.intents.actions.ActionTransfer
import com.getcode.model.intents.actions.ActionWithdraw
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.*
import com.getcode.solana.organizer.AccountCluster
import com.getcode.solana.organizer.AccountType
import com.getcode.solana.organizer.Organizer
import com.getcode.solana.organizer.Tray
import timber.log.Timber

class IntentPublicTransfer(
    override val id: PublicKey,
    private val organizer: Organizer,
    private val sourceCluster: AccountCluster,
    private val destination: PublicKey,
    private val amount: KinAmount,

    val resultTray: Tray,

    override val actionGroup: ActionGroup,
) : IntentType() {
    override fun metadata(): TransactionService.Metadata {
        return TransactionService.Metadata.newBuilder()
            .setSendPublicPayment(
                TransactionService.SendPublicPaymentMetadata.newBuilder()
                    .setSource(sourceCluster.timelockAccounts.vault.publicKey.bytes.toSolanaAccount())
                    .setDestination(destination.bytes.toSolanaAccount())
                    .setIsWithdrawal(true)
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

    sealed interface Destination {
        data class Local(val accountType: AccountType): Destination
        data class External(val publicKey: PublicKey): Destination
    }

    companion object {
        fun newInstance(
            organizer: Organizer,
            source: AccountType,
            destination: Destination,
            amount: KinAmount,
        ): IntentPublicTransfer {
            val id = PublicKey.generate()
            val currentTray = organizer.tray.copy()
            val sourceCluster = organizer.tray.cluster(source)


            val target = when (destination) {
                is Destination.External -> destination.publicKey
                is Destination.Local -> organizer.tray.cluster(destination.accountType).timelockAccounts.vault.publicKey
            }

            // 1. Transfer all funds in the primary account
            // directly to the destination. This is a public
            // transfer so no buckets involved and no rotation
            // required.

            val transfer = ActionTransfer.newInstance(
                kind = ActionTransfer.Kind.NoPrivacyTransfer,
                intentId = id,
                amount = amount.kin,
                source = sourceCluster,
                destination = target
            )

            currentTray.decrement(source, kin = amount.kin)

            // If moving funds to an already known account
            // we should update the balance accordingly
            if (destination is Destination.Local) {
                currentTray.increment(destination.accountType, amount.kin)
            }

            return IntentPublicTransfer(
                id = id,
                organizer = organizer,
                sourceCluster = sourceCluster,
                destination = target,
                amount = amount,
                actionGroup = ActionGroup().apply {
                    actions = listOf(transfer)
                },
                resultTray = currentTray,
            )

        }
    }
}

sealed class IntentPublicTransferException: Exception() {
    class BalanceMismatchException: IntentPublicTransferException()
}